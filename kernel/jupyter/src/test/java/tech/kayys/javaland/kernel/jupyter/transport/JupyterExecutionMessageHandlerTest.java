package tech.kayys.javaland.kernel.jupyter.transport;

import org.junit.jupiter.api.Test;
import tech.kayys.javaland.protocol.jupyter.JupyterMessage;
import tech.kayys.javaland.protocol.jupyter.JupyterMessageHeader;
import tech.kayys.javaland.runtime.JavaExecutionSession;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JupyterExecutionMessageHandlerTest {
    @Test
    void answersKernelInfoRequestsOnShellChannel() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound("kernel_info_request", Map.of()));
            assertEquals(1, responses.size());
            var response = responses.getFirst();
            assertEquals(JupyterZmqChannel.SHELL, response.channel());
            assertEquals("kernel_info_reply", response.message().header().messageType());
            assertEquals("javaland", response.message().content().get("implementation"));
            assertEquals("5.3", response.message().content().get("protocol_version"));
            assertEquals("request-1", response.message().parentHeader().get("msg_id"));
        }
    }

    @Test
    void executesExpressionAndPublishesResultBetweenBusyAndIdle() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound("execute_request",
                    Map.of("code", "6 * 7", "silent", false, "store_history", true)));

            assertEquals(List.of("status", "execute_input", "execute_result", "execute_reply", "status"),
                    responses.stream().map(item -> item.message().header().messageType())
                            .toList());
            assertEquals(JupyterZmqChannel.IOPUB, responses.get(0).channel());
            assertEquals(JupyterZmqChannel.SHELL, responses.get(3).channel());
            assertEquals("busy", responses.get(0).message().content().get("execution_state"));
            assertEquals("42", ((Map<?, ?>) responses.get(2).message().content().get("data"))
                    .get("text/plain"));
            assertEquals("ok", responses.get(3).message().content().get("status"));
            assertEquals("idle", responses.get(4).message().content().get("execution_state"));
            assertEquals("execute_result.request-session",
                    new String(responses.get(2).identities().getFirst(),
                            java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void reportsExecutionFailuresInReplyAndIopub() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound("execute_request",
                    Map.of("code", "int value = ;", "silent", false)));
            assertTrue(responses.stream().anyMatch(item ->
                    item.message().header().messageType().equals("error")));
            var reply = responses.stream()
                    .filter(item -> item.message().header().messageType().equals("execute_reply"))
                    .findFirst().orElseThrow();
            assertEquals("error", reply.message().content().get("status"));
        }
    }

    @Test
    void silentExecutionDoesNotPublishInputOrResultOrAdvanceHistory() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var silent = handler.handle(inbound("execute_request",
                    Map.of("code", "6 * 7", "silent", true, "store_history", true)));
            assertEquals(List.of("status", "execute_reply", "status"),
                    silent.stream().map(item -> item.message().header().messageType()).toList());
            assertEquals(0, silent.get(1).message().content().get("execution_count"));

            var visible = handler.handle(inbound("execute_request",
                    Map.of("code", "7 * 6", "silent", false, "store_history", true)));
            var reply = visible.stream()
                    .filter(item -> item.message().header().messageType().equals("execute_reply"))
                    .findFirst().orElseThrow();
            assertEquals(1, reply.message().content().get("execution_count"));
        }
    }

    private static JupyterInboundMessage inbound(String type, Map<String, Object> content) {
        var header = new JupyterMessageHeader(
                "request-1", "test-user", "request-session", Instant.now().toString(), type, "5.3");
        return new JupyterInboundMessage(JupyterZmqChannel.SHELL, List.of(new byte[]{1}),
                new JupyterMessage(header, Map.of(), Map.of(), content));
    }
}
