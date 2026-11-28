package tech.kayys.javaland.kernel.jupyter.transport;

import org.dflib.jjava.jupyter.kernel.LanguageInfo;
import org.dflib.jjava.jupyter.kernel.ReplacementOptions;
import org.dflib.jjava.jupyter.kernel.display.DisplayData;
import org.dflib.jjava.jupyter.kernel.display.mime.MIMEType;
import tech.kayys.javaland.kernel.jupyter.JavaLandKernel;
import tech.kayys.javaland.kernel.jupyter.NotebookExecutionResult;
import tech.kayys.javaland.kernel.jupyter.NotebookDisplayEvent;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import tech.kayys.javaland.protocol.jupyter.JupyterMessage;
import tech.kayys.javaland.protocol.jupyter.JupyterMessageHeader;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Protocol adapter for the feature-rich JavaLand notebook kernel.
 *
 * <p>Unlike {@link JupyterExecutionMessageHandler}, this adapter delegates evaluation,
 * magic dispatch, completion and lifecycle to {@link JavaLandKernel}. It deliberately
 * does not replace the active launcher yet; stream capture and the remaining notebook
 * protocol features must be migrated before that switch.</p>
 */
public final class JavaLandKernelMessageHandler implements JupyterAsyncMessageHandler, AutoCloseable {
    private static final String PROTOCOL_VERSION = "5.3";
    private static final String IMPLEMENTATION_VERSION = "0.1.0";
    private static final List<String> DISPLAY_MIME_TYPES = List.of(
            "text/plain", "text/html", "text/markdown", "text/latex",
            "application/json", "application/javascript",
            "image/png", "image/jpeg", "image/gif", "image/svg+xml", "audio/wav");

    private final JavaLandKernel kernel;
    private final JupyterStdinInputStream stdin;
    private final ConcurrentLinkedQueue<JupyterOutboundMessage> asyncMessages = new ConcurrentLinkedQueue<>();
    private final NotebookDisplayPublisher displayPublisher = new NotebookDisplayPublisher();
    private final AtomicReference<JupyterInboundMessage> activeExecution = new AtomicReference<>();
    private int executionCount;

    public JavaLandKernelMessageHandler() {
        this.stdin = new JupyterStdinInputStream(this::requestInput);
        this.kernel = new JavaLandKernel(stdin);
    }

    /** Creates a handler around an externally managed kernel without notebook stdin injection. */
    public JavaLandKernelMessageHandler(JavaLandKernel kernel) {
        this.kernel = Objects.requireNonNull(kernel, "kernel must not be null");
        this.stdin = null;
    }

    private void requestInput(String prompt, boolean password) {
        JupyterInboundMessage parent = activeExecution.get();
        if (parent == null || stdin == null) {
            throw new IllegalStateException("No active notebook execution can receive input");
        }
        asyncMessages.add(outbound(parent, JupyterZmqChannel.STDIN, parent.identities(),
                "input_request", Map.of("prompt", prompt, "password", password)));
    }

    /**
     * Publishes a rich IOPub display associated with the supplied notebook request.
     * The returned identifier can be used with {@link #updateDisplay}.
     */
    public NotebookDisplayPublisher.PublishedDisplay publishDisplay(
            JupyterInboundMessage parent,
            Map<String, Object> data,
            Map<String, Object> metadata) {
        return displayPublisher.display(parent, data, metadata);
    }

    /** Updates a display previously published by this handler. */
    public JupyterOutboundMessage updateDisplay(
            JupyterInboundMessage parent,
            String displayId,
            Map<String, Object> data,
            Map<String, Object> metadata) {
        return displayPublisher.updateDisplay(parent, displayId, data, metadata);
    }

    /** Clears the frontend output area and invalidates this handler's display identifiers. */
    public JupyterOutboundMessage clearOutput(JupyterInboundMessage parent, boolean wait) {
        return displayPublisher.clearOutput(parent, wait);
    }

    @Override
    public List<JupyterOutboundMessage> drainAsyncMessages() {
        List<JupyterOutboundMessage> drained = new ArrayList<>();
        JupyterOutboundMessage message;
        while ((message = asyncMessages.poll()) != null) {
            drained.add(message);
        }
        return List.copyOf(drained);
    }

    @Override
    public List<JupyterOutboundMessage> handle(JupyterInboundMessage inbound) throws Exception {
        Objects.requireNonNull(inbound, "inbound must not be null");
        if (inbound.channel() == JupyterZmqChannel.STDIN) {
            if (!"input_reply".equals(inbound.message().header().messageType()) || stdin == null) {
                return List.of();
            }
            JupyterInboundMessage execution = activeExecution.get();
            Object parentId = inbound.message().parentHeader().get("msg_id");
            if (execution == null || !execution.message().header().messageId().equals(parentId)) {
                return List.of();
            }
            Object value = inbound.message().content().get("value");
            stdin.submitIfPending(stringValue(value, ""));
            return List.of();
        }
        if (inbound.channel() == JupyterZmqChannel.CONTROL) {
            if (!"interrupt_request".equals(inbound.message().header().messageType())) {
                return List.of();
            }
            if (stdin != null) {
                stdin.cancelPending();
            }
            kernel.interrupt();
            return List.of(outbound(inbound, JupyterZmqChannel.CONTROL, inbound.identities(),
                    "interrupt_reply", Map.of()));
        }
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

    private List<JupyterOutboundMessage> execute(JupyterInboundMessage inbound) {
        activeExecution.set(inbound);
        try {
            return executeActive(inbound);
        } finally {
            activeExecution.compareAndSet(inbound, null);
        }
    }

    private List<JupyterOutboundMessage> executeActive(JupyterInboundMessage inbound) {
        Map<String, Object> request = inbound.message().content();
        String code = stringValue(request.get("code"), "");
        boolean silent = booleanValue(request.get("silent"), false);
        boolean storeHistory = booleanValue(request.get("store_history"), true);
        int count = storeHistory && !silent ? ++executionCount : executionCount;
        List<JupyterOutboundMessage> messages = new ArrayList<>();
        messages.add(iopub(inbound, "status", Map.of("execution_state", "busy")));
        if (!silent) {
            messages.add(iopub(inbound, "execute_input",
                    Map.of("code", code, "execution_count", count)));
        }

        try {
            NotebookExecutionResult execution = kernel.evalWithOutput(code);
            if (!silent) {
                publishStream(messages, inbound, "stdout", execution.stdout());
                publishStream(messages, inbound, "stderr", execution.stderr());
            }

            if (!silent) {
                publishDisplayEvents(messages, inbound, execution.displayEvents());
            }

            if (execution.successful()) {
                DisplayData result = execution.displayData();
                if (!silent && result != null && !DISPLAY_MIME_TYPES.stream()
                        .allMatch(mime -> dataFor(result, mime) == null)) {
                    JupyterMimeBundle bundle = JupyterMimeBundle.from(displayData(result));
                    Map<String, Object> content = new LinkedHashMap<>();
                    content.put("execution_count", count);
                    content.put("data", bundle.data());
                    content.put("metadata", Map.of());
                    if (!bundle.bufferPaths().isEmpty()) {
                        content.put("buffer_paths", bundle.bufferPaths());
                    }
                    messages.add(iopub(inbound, "execute_result", content, bundle.buffers()));
                }
                messages.add(reply(inbound, "execute_reply", Map.of(
                        "status", "ok", "execution_count", count,
                        "payload", List.of(), "user_expressions", Map.of())));
            } else {
                Exception failure = execution.failure();
                String name = failure.getClass().getSimpleName();
                String value = failure.getMessage() == null ? "" : failure.getMessage();
                if (!silent) {
                    messages.add(iopub(inbound, "error", Map.of(
                            "ename", name, "evalue", value,
                            "traceback", List.of(name + ": " + value))));
                }
                messages.add(reply(inbound, "execute_reply", Map.of(
                        "status", "error", "execution_count", count,
                        "ename", name, "evalue", value,
                        "traceback", List.of(name + ": " + value),
                        "payload", List.of(), "user_expressions", Map.of())));
            }
        } catch (Exception failure) {
            String name = failure.getClass().getSimpleName();
            String value = failure.getMessage() == null ? "" : failure.getMessage();
            if (!silent) {
                messages.add(iopub(inbound, "error", Map.of(
                        "ename", name, "evalue", value,
                        "traceback", List.of(name + ": " + value))));
            }
            messages.add(reply(inbound, "execute_reply", Map.of(
                    "status", "error", "execution_count", count,
                    "ename", name, "evalue", value,
                    "traceback", List.of(name + ": " + value),
                    "payload", List.of(), "user_expressions", Map.of())));
        }
        messages.add(iopub(inbound, "status", Map.of("execution_state", "idle")));
        return List.copyOf(messages);
    }

    private void publishDisplayEvents(
            List<JupyterOutboundMessage> messages,
            JupyterInboundMessage inbound,
            List<NotebookDisplayEvent> events) {
        for (NotebookDisplayEvent event : events) {
            switch (event.type()) {
                case DISPLAY -> messages.add(displayPublisher.display(
                        inbound, event.displayId(), event.data(), event.metadata()).message());
                case UPDATE -> messages.add(displayPublisher.updateDisplay(
                        inbound, event.displayId(), event.data(), event.metadata()));
                case CLEAR -> messages.add(displayPublisher.clearOutput(inbound, event.wait()));
            }
        }
    }

    private static void publishStream(
            List<JupyterOutboundMessage> messages,
            JupyterInboundMessage inbound,
            String name,
            String text) {
        if (text != null && !text.isEmpty()) {
            messages.add(iopub(inbound, "stream", Map.of("name", name, "text", text)));
        }
    }

    private List<JupyterOutboundMessage> complete(JupyterInboundMessage inbound) throws Exception {
        Map<String, Object> request = inbound.message().content();
        String code = stringValue(request.get("code"), "");
        int cursor = integerValue(request.get("cursor_pos"), code.length());
        int boundedCursor = Math.max(0, Math.min(cursor, code.length()));
        ReplacementOptions options = kernel.complete(code, boundedCursor);
        List<String> matches = options == null ? List.of() : options.getReplacements();
        int cursorStart = options == null ? boundedCursor : options.getSourceStart();
        int cursorEnd = options == null ? boundedCursor : options.getSourceEnd();
        return List.of(reply(inbound, "complete_reply", Map.of(
                "status", "ok", "matches", matches,
                "cursor_start", cursorStart, "cursor_end", cursorEnd,
                "metadata", Map.of())));
    }

    private List<JupyterOutboundMessage> isComplete(JupyterInboundMessage inbound) {
        String code = stringValue(inbound.message().content().get("code"), "");
        String status = kernel.isComplete(code);
        Map<String, Object> content = status == null || status.isBlank()
                ? Map.of("status", "incomplete", "indent", "    ")
                : Map.of("status", "complete");
        return List.of(reply(inbound, "is_complete_reply", content));
    }

    private Map<String, Object> kernelInfo() {
        LanguageInfo language = kernel.getLanguageInfo();
        Map<String, Object> languageInfo = new LinkedHashMap<>();
        languageInfo.put("name", language.getName());
        languageInfo.put("version", language.getVersion());
        languageInfo.put("mimetype", language.getMimetype());
        languageInfo.put("file_extension", language.getFileExtension());
        languageInfo.put("pygments_lexer", language.getPygmentsLexer());
        languageInfo.put("codemirror_mode", language.getCodemirrorMode());
        languageInfo.put("nbconvert_exporter", "script");
        return Map.of(
                "protocol_version", PROTOCOL_VERSION,
                "implementation", "javaland",
                "implementation_version", IMPLEMENTATION_VERSION,
                "language_info", languageInfo,
                "banner", kernel.getBanner(),
                "help_links", List.of());
    }

    private static Map<String, Object> displayData(DisplayData display) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String mime : DISPLAY_MIME_TYPES) {
            Object value = dataFor(display, mime);
            if (value != null) {
                data.put(mime, value);
            }
        }
        if (!data.containsKey("text/plain")) {
            data.put("text/plain", "");
        }
        return data;
    }

    private static Object dataFor(DisplayData display, String mime) {
        return display.getData(MIMEType.parse(mime));
    }

    private static JupyterOutboundMessage reply(
            JupyterInboundMessage inbound, String type, Map<String, Object> content) {
        return outbound(inbound, JupyterZmqChannel.SHELL, inbound.identities(), type, content);
    }

    private static JupyterOutboundMessage iopub(
            JupyterInboundMessage inbound, String type, Map<String, Object> content) {
        return iopub(inbound, type, content, List.of());
    }

    private static JupyterOutboundMessage iopub(
            JupyterInboundMessage inbound, String type, Map<String, Object> content,
            List<byte[]> buffers) {
        String topic = type + "." + inbound.message().header().session();
        return outbound(inbound, JupyterZmqChannel.IOPUB,
                List.of(topic.getBytes(StandardCharsets.UTF_8)), type, content, buffers);
    }

    private static JupyterOutboundMessage outbound(
            JupyterInboundMessage inbound, JupyterZmqChannel channel, List<byte[]> identities,
            String type, Map<String, Object> content) {
        return outbound(inbound, channel, identities, type, content, List.of());
    }

    private static JupyterOutboundMessage outbound(
            JupyterInboundMessage inbound, JupyterZmqChannel channel, List<byte[]> identities,
            String type, Map<String, Object> content, List<byte[]> buffers) {
        JupyterMessageHeader request = inbound.message().header();
        JupyterMessageHeader header = new JupyterMessageHeader(
                UUID.randomUUID().toString(), request.username(), request.session(),
                Instant.now().toString(), type, PROTOCOL_VERSION);
        Map<String, Object> parent = Map.of(
                "msg_id", request.messageId(), "username", request.username(),
                "session", request.session(), "date", request.date(),
                "msg_type", request.messageType(), "version", request.version());
        return new JupyterOutboundMessage(channel, identities,
                new JupyterMessage(header, parent, Map.of(), content, buffers));
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
        if (stdin != null) {
            stdin.close();
        }
        kernel.onShutdown(false);
    }
}
