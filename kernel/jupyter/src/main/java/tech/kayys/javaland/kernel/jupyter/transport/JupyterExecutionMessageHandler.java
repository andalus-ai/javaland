package tech.kayys.javaland.kernel.jupyter.transport;

import tech.kayys.javaland.protocol.jupyter.JupyterMessage;
import tech.kayys.javaland.protocol.jupyter.JupyterMessageHeader;
import tech.kayys.javaland.runtime.JavaExecutionSession;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** JavaLand-owned adapter for the core Jupyter kernel request/reply messages. */
public final class JupyterExecutionMessageHandler implements JupyterMessageHandler, AutoCloseable {
    private static final String PROTOCOL_VERSION = "5.3";
    private static final String IMPLEMENTATION_VERSION = "0.1.0";
    private static final String INCOMPLETE_INDENT = "    ";

    private final JavaExecutionSession execution;
    private int executionCount;

    public JupyterExecutionMessageHandler() {
        this(JavaExecutionSession.create());
    }

    public JupyterExecutionMessageHandler(JavaExecutionSession execution) {
        this.execution = Objects.requireNonNull(execution, "execution must not be null");
    }

    @Override
    public List<JupyterOutboundMessage> handle(JupyterInboundMessage inbound) throws Exception {
        Objects.requireNonNull(inbound, "inbound must not be null");
        if (inbound.channel() != JupyterZmqChannel.SHELL) {
            return List.of();
        }

        return switch (inbound.message().header().messageType()) {
            case "kernel_info_request" -> List.of(reply(inbound, "kernel_info_reply", kernelInfo()));
            case "execute_request" -> execute(inbound);
            case "complete_request" -> complete(inbound);
            case "is_complete_request" -> isComplete(inbound);
            default -> List.of();
        };
    }

    private List<JupyterOutboundMessage> isComplete(JupyterInboundMessage inbound) {
        String code = stringValue(inbound.message().content().get("code"), "");
        String status = execution.isComplete(code);
        Map<String, Object> content = status.equals("complete")
                ? Map.of("status", "complete")
                : Map.of("status", "incomplete", "indent", INCOMPLETE_INDENT);
        return List.of(reply(inbound, "is_complete_reply", content));
    }

    private List<JupyterOutboundMessage> complete(JupyterInboundMessage inbound) {
        Map<String, Object> request = inbound.message().content();
        String code = stringValue(request.get("code"), "");
        int cursor = integerValue(request.get("cursor_pos"), code.length());
        try {
            JavaExecutionSession.CompletionResult result = execution.complete(code, cursor);
            return List.of(reply(inbound, "complete_reply", Map.of(
                    "status", "ok",
                    "matches", result.matches(),
                    "cursor_start", result.cursorStart(),
                    "cursor_end", cursor,
                    "metadata", Map.of())));
        } catch (IllegalArgumentException invalidCursor) {
            return List.of(reply(inbound, "complete_reply", Map.of(
                    "status", "ok",
                    "matches", List.of(),
                    "cursor_start", Math.max(0, Math.min(cursor, code.length())),
                    "cursor_end", Math.max(0, Math.min(cursor, code.length())),
                    "metadata", Map.of())));
        }
    }

    private List<JupyterOutboundMessage> execute(JupyterInboundMessage inbound) {
        JupyterMessage request = inbound.message();
        Map<String, Object> content = request.content();
        String code = stringValue(content.get("code"), "");
        boolean silent = booleanValue(content.get("silent"), false);
        boolean storeHistory = booleanValue(content.get("store_history"), true);
        int nextCount = storeHistory && !silent ? ++executionCount : executionCount;

        List<JupyterOutboundMessage> outbound = new ArrayList<>();
        outbound.add(iopub(inbound, "status", Map.of("execution_state", "busy")));
        if (!silent) {
            outbound.add(iopub(inbound, "execute_input",
                    Map.of("code", code, "execution_count", nextCount)));
        }

        try {
            Object value = execution.eval(code);
            if (!silent && value != null) {
                outbound.add(iopub(inbound, "execute_result", Map.of(
                        "execution_count", nextCount,
                        "data", Map.of("text/plain", value.toString()),
                        "metadata", Map.of())));
            }
            outbound.add(reply(inbound, "execute_reply", Map.of(
                    "status", "ok",
                    "execution_count", nextCount,
                    "payload", List.of(),
                    "user_expressions", Map.of())));
        } catch (Exception failure) {
            String errorName = failure.getClass().getSimpleName();
            String errorValue = failure.getMessage() == null ? "" : failure.getMessage();
            if (!silent) {
                outbound.add(iopub(inbound, "error", Map.of(
                        "ename", errorName,
                        "evalue", errorValue,
                        "traceback", List.of(errorName + ": " + errorValue))));
            }
            outbound.add(reply(inbound, "execute_reply", Map.of(
                    "status", "error",
                    "execution_count", nextCount,
                    "ename", errorName,
                    "evalue", errorValue,
                    "traceback", List.of(errorName + ": " + errorValue),
                    "payload", List.of(),
                    "user_expressions", Map.of())));
        }

        outbound.add(iopub(inbound, "status", Map.of("execution_state", "idle")));
        return List.copyOf(outbound);
    }

    private static Map<String, Object> kernelInfo() {
        return Map.of(
                "protocol_version", PROTOCOL_VERSION,
                "implementation", "javaland",
                "implementation_version", IMPLEMENTATION_VERSION,
                "language_info", Map.of(
                        "name", "java",
                        "version", Runtime.version().toString(),
                        "mimetype", "text/x-java-source",
                        "file_extension", ".java",
                        "pygments_lexer", "java",
                        "codemirror_mode", "java",
                        "nbconvert_exporter", "script"),
                "banner", "JavaLand — Java " + Runtime.version(),
                "help_links", List.of());
    }

    private static JupyterOutboundMessage reply(
            JupyterInboundMessage inbound, String messageType, Map<String, Object> content) {
        return outbound(inbound, JupyterZmqChannel.SHELL, List.of(), messageType, content);
    }

    private static JupyterOutboundMessage iopub(
            JupyterInboundMessage inbound, String messageType, Map<String, Object> content) {
        String topic = messageType + "." + inbound.message().header().session();
        return outbound(inbound, JupyterZmqChannel.IOPUB,
                List.of(topic.getBytes(StandardCharsets.UTF_8)), messageType, content);
    }

    private static JupyterOutboundMessage outbound(
            JupyterInboundMessage inbound, JupyterZmqChannel channel, List<byte[]> identities,
            String messageType, Map<String, Object> content) {
        JupyterMessageHeader requestHeader = inbound.message().header();
        JupyterMessageHeader header = new JupyterMessageHeader(
                UUID.randomUUID().toString(), requestHeader.username(), requestHeader.session(),
                Instant.now().toString(), messageType, PROTOCOL_VERSION);
        Map<String, Object> parentHeader = Map.of(
                "msg_id", requestHeader.messageId(),
                "username", requestHeader.username(),
                "session", requestHeader.session(),
                "date", requestHeader.date(),
                "msg_type", requestHeader.messageType(),
                "version", requestHeader.version());
        return new JupyterOutboundMessage(channel, identities,
                new JupyterMessage(header, parentHeader, Map.of(), content));
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private static int integerValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    @Override
    public void close() {
        execution.close();
    }
}
