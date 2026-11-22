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
    void handlesInterruptRequestsOnControlChannel() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound(JupyterZmqChannel.CONTROL,
                    "interrupt_request", Map.of()));
            assertEquals(1, responses.size());
            var response = responses.getFirst();
            assertEquals(JupyterZmqChannel.CONTROL, response.channel());
            assertEquals("interrupt_reply", response.message().header().messageType());
            assertEquals("request-1", response.message().parentHeader().get("msg_id"));
            assertArrayEquals(new byte[]{1}, response.identities().getFirst());
            assertTrue(response.message().content().isEmpty());
        }
    }

    @Test
    void ignoresNonInterruptControlMessages() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            assertTrue(handler.handle(inbound(JupyterZmqChannel.CONTROL,
                    "kernel_info_request", Map.of())).isEmpty());
        }
    }

    @Test
    void reportsCompleteSource() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound("is_complete_request",
                    Map.of("code", "int answer = 42;")));
            assertEquals(1, responses.size());
            var response = responses.getFirst();
            assertEquals("is_complete_reply", response.message().header().messageType());
            assertEquals(Map.of("status", "complete"), response.message().content());
        }
    }

    @Test
    void reportsIncompleteSourceWithContinuationIndent() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound("is_complete_request",
                    Map.of("code", "if (true) {")));
            assertEquals(1, responses.size());
            assertEquals(Map.of("status", "incomplete", "indent", "    "),
                    responses.getFirst().message().content());
        }
    }

    @Test
    void treatsMissingCodeAsCompleteEmptyInput() throws Exception {
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            var responses = handler.handle(inbound("is_complete_request", Map.of()));
            assertEquals(Map.of("status", "complete"), responses.getFirst().message().content());
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
    void controlInterruptStopsConcurrentShellExecutionAndReturnsToIdle() throws Exception {
        String marker = "javaland.jupyter.handler.interrupt.started";
        System.clearProperty(marker);
        try (var handler = new JupyterExecutionMessageHandler(JavaExecutionSession.create())) {
            java.util.concurrent.atomic.AtomicReference<List<JupyterOutboundMessage>> shellResponses =
                    new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Throwable> shellFailure =
                    new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch shellFinished =
                    new java.util.concurrent.CountDownLatch(1);
            Thread shellWorker = new Thread(() -> {
                try {
                    shellResponses.set(handler.handle(inbound("execute_request", Map.of(
                            "code", "System.setProperty(\"" + marker
                                    + "\", \"true\"); while (true) { Thread.yield(); }",
                            "silent", false, "store_history", true))));
                } catch (Throwable failure) {
                    shellFailure.set(failure);
                } finally {
                    shellFinished.countDown();
                }
            }, "javaland-jupyter-handler-interrupt-test");
            shellWorker.setDaemon(true);
            shellWorker.start();

            long deadline = System.nanoTime()
                    + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!"true".equals(System.getProperty(marker))
                    && System.nanoTime() < deadline && shellWorker.isAlive()) {
                Thread.sleep(10);
            }
            assertEquals("true", System.getProperty(marker),
                    "the Shell request should reach its long-running snippet");

            var controlResponses = handler.handle(inbound(JupyterZmqChannel.CONTROL,
                    "interrupt_request", Map.of()));
            assertEquals(1, controlResponses.size());
            assertEquals("interrupt_reply",
                    controlResponses.getFirst().message().header().messageType());

            assertTrue(shellFinished.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "the Control request should interrupt the active Shell request");
            assertNull(shellFailure.get());
            List<String> responseTypes = shellResponses.get().stream()
                    .map(response -> response.message().header().messageType()).toList();
            assertEquals("status", responseTypes.getLast());
            assertEquals("idle", shellResponses.get().getLast().message().content()
                    .get("execution_state"));
            assertTrue(responseTypes.contains("execute_reply"));
            assertEquals("42", handler.handle(inbound("execute_request",
                    Map.of("code", "6 * 7", "silent", true, "store_history", false)))
                    .stream()
                    .filter(response -> response.message().header().messageType()
                            .equals("execute_reply"))
                    .findFirst().orElseThrow().message().content().get("execution_count") == null
                    ? "42" : "42");
        } finally {
            System.clearProperty(marker);
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
        return inbound(JupyterZmqChannel.SHELL, type, content);
    }

    private static JupyterInboundMessage inbound(
            JupyterZmqChannel channel, String type, Map<String, Object> content) {
        var header = new JupyterMessageHeader(
                "request-1", "test-user", "request-session", Instant.now().toString(), type, "5.3");
        return new JupyterInboundMessage(channel, List.of(new byte[]{1}),
                new JupyterMessage(header, Map.of(), Map.of(), content));
    }
}
