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
    void completesAgainstPersistentSessionAndReturnsJupyterOffsets() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            handler.handle(inbound("execute_request", Map.of(
                    "code", "void greetJavaLand() {}", "silent", true, "store_history", false)));
            var responses = handler.handle(inbound("complete_request",
                    Map.of("code", "greetJ", "cursor_pos", 6)));

            assertEquals(1, responses.size());
            var response = responses.getFirst();
            assertEquals("complete_reply", response.message().header().messageType());
            assertEquals("ok", response.message().content().get("status"));
            assertEquals(0, response.message().content().get("cursor_start"));
            assertEquals(6, response.message().content().get("cursor_end"));
            assertTrue((List<?>) response.message().content().get("matches"))
                    .contains("greetJavaLand");
        }
    }

    @Test
    void returnsEmptyCompletionForOutOfRangeCursor() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound("complete_request",
                    Map.of("code", "hello", "cursor_pos", 99)));
            assertEquals(List.of(), responses.getFirst().message().content().get("matches"));
            assertEquals(5, responses.getFirst().message().content().get("cursor_start"));
            assertEquals(5, responses.getFirst().message().content().get("cursor_end"));
        }
    }

    @Test
    void executesExpressionAndPublishesResultBetweenBusyAndIdle() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound("execute_request",
                    Map.of("code", "6 * 7", "silent", false, "store_history", true)));
            assertEquals(List.of("status", "execute_input", "execute_result", "execute_reply", "status"),
                    responses.stream().map(item -> item.message().header().messageType()).toList());
            assertEquals("42", ((Map<?, ?>) responses.get(2).message().content().get("data"))
                    .get("text/plain"));
        }
    }

    private static JupyterInboundMessage inbound(String type, Map<String, Object> content) {
        var header = new JupyterMessageHeader(
                "request-1", "test-user", "request-session", Instant.now().toString(), type, "5.3");
        return new JupyterInboundMessage(JupyterZmqChannel.SHELL, List.of(new byte[]{1}),
                new JupyterMessage(header, Map.of(), Map.of(), content));
    }
}
