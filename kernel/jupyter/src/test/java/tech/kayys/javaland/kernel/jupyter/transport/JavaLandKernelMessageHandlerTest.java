package tech.kayys.javaland.kernel.jupyter.transport;

import org.junit.jupiter.api.Test;
import tech.kayys.javaland.kernel.jupyter.JavaLandKernel;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavaLandKernelMessageHandlerTest {
    @Test
    void kernelInfoUsesTheFeatureRichJavaLandKernelMetadata() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler(new JavaLandKernel())) {
            var response = handler.handle(inbound("kernel_info_request", Map.of())).getFirst();

            assertEquals("kernel_info_reply", response.message().header().messageType());
            assertEquals("javaland", response.message().content().get("implementation"));
            assertTrue(((String) response.message().content().get("banner"))
                    .contains("JavaLand Kernel"));
        }
    }

    @Test
    void executeRequestRunsNotebookMagicsAndPreservesRichMimeOutput() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler(new JavaLandKernel())) {
            var responses = handler.handle(inbound("execute_request", Map.of(
                    "code", "%magics", "silent", false, "store_history", true)));

            var result = responses.stream()
                    .filter(item -> "execute_result".equals(item.message().header().messageType()))
                    .findFirst().orElseThrow();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.message().content().get("data");

            assertTrue(data.containsKey("text/plain"));
            assertTrue(data.containsKey("text/html"));
            assertTrue(responses.stream().anyMatch(item ->
                    "execute_reply".equals(item.message().header().messageType())));
        }
    }

    @Test
    void javaCodeCanPublishUpdateAndClearRichDisplays() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler(new JavaLandKernel())) {
            var responses = handler.handle(inbound("execute_request", Map.of(
                    "code", "String displayId = notebookDisplay.display(Map.of(\"text/plain\", \"first\", \"text/html\", \"<b>first</b>\")); notebookDisplay.update(displayId, Map.of(\"text/plain\", \"second\")); notebookDisplay.clear(true);",
                    "silent", false, "store_history", true)));

            var displayMessages = responses.stream()
                    .filter(item -> List.of("display_data", "update_display_data", "clear_output")
                            .contains(item.message().header().messageType()))
                    .toList();
            assertEquals(List.of("display_data", "update_display_data", "clear_output"),
                    displayMessages.stream().map(item -> item.message().header().messageType()).toList());
            assertEquals("true", String.valueOf(displayMessages.getLast()
                    .message().content().get("wait")));
            @SuppressWarnings("unchecked")
            Map<String, Object> transientMetadata = (Map<String, Object>)
                    displayMessages.getFirst().message().content().get("transient");
            String displayId = (String) transientMetadata.get("display_id");
            @SuppressWarnings("unchecked")
            Map<String, Object> updateTransient = (Map<String, Object>)
                    displayMessages.get(1).message().content().get("transient");
            assertEquals(displayId, updateTransient.get("display_id"));
        }
    }

    @Test
    void handlerExposesDisplayPublishUpdateAndClearOperations() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler(new JavaLandKernel())) {
            var request = inbound("execute_request", Map.of("code", "", "silent", true));
            var published = handler.publishDisplay(request,
                    Map.of("text/plain", "initial"), Map.of());
            assertEquals("display_data",
                    published.message().message().header().messageType());

            var update = handler.updateDisplay(request, published.displayId(),
                    Map.of("text/plain", "updated"), Map.of());
            assertEquals("update_display_data", update.message().header().messageType());

            var clear = handler.clearOutput(request, false);
            assertEquals("clear_output", clear.message().header().messageType());
        }
    }

    @Test
    void completionAndCompletenessUseTheKernelOwnedJshellSession() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler(new JavaLandKernel())) {
            handler.handle(inbound("execute_request", Map.of(
                    "code", "void javaLandAdapterProbe() {}", "silent", true,
                    "store_history", false)));

            var completion = handler.handle(inbound("complete_request",
                    Map.of("code", "javaLandAdapterP", "cursor_pos", 17))).getFirst();
            assertEquals("complete_reply", completion.message().header().messageType());
            assertEquals("ok", completion.message().content().get("status"));
            assertTrue((List<?>) completion.message().content().get("matches"))
                    .contains("javaLandAdapterProbe");

            var complete = handler.handle(inbound("is_complete_request",
                    Map.of("code", "int value = 42;"))).getFirst();
            assertEquals(Map.of("status", "complete"), complete.message().content());

            var incomplete = handler.handle(inbound("is_complete_request",
                    Map.of("code", "if (true) {"))).getFirst();
            assertEquals("incomplete", incomplete.message().content().get("status"));
        }
    }

    @Test
    void controlInterruptIsDelegatedToTheKernel() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler(new JavaLandKernel())) {
            var response = handler.handle(inbound(JupyterZmqChannel.CONTROL,
                    "interrupt_request", Map.of())).getFirst();
            assertEquals(JupyterZmqChannel.CONTROL, response.channel());
            assertEquals("interrupt_reply", response.message().header().messageType());
        }
    }

    @Test
    void executePublishesCellScopedStdoutAndStderrEvents() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler(new JavaLandKernel())) {
            var responses = handler.handle(inbound("execute_request", Map.of(
                    "code", "System.out.println(\"hello from cell\"); System.err.println(\"warning from cell\");",
                    "silent", false, "store_history", true)));

            var streams = responses.stream()
                    .filter(item -> "stream".equals(item.message().header().messageType()))
                    .toList();
            assertEquals(2, streams.size());
            assertEquals("stdout", streams.get(0).message().content().get("name"));
            assertEquals("hello from cell" + System.lineSeparator(),
                    streams.get(0).message().content().get("text"));
            assertEquals("stderr", streams.get(1).message().content().get("name"));
            assertEquals("warning from cell" + System.lineSeparator(),
                    streams.get(1).message().content().get("text"));

            var next = handler.handle(inbound("execute_request", Map.of(
                    "code", "1 + 1", "silent", false, "store_history", true)));
            assertTrue(next.stream().noneMatch(item ->
                    "stream".equals(item.message().header().messageType())));
        }
    }

    @Test
    void executePreservesOutputProducedBeforeAnException() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler(new JavaLandKernel())) {
            var responses = handler.handle(inbound("execute_request", Map.of(
                    "code", "System.out.println(\"before failure\"); throw new IllegalStateException(\"boom\");",
                    "silent", false, "store_history", true)));

            assertTrue(responses.stream().anyMatch(item ->
                    "stream".equals(item.message().header().messageType())
                            && "before failure".equals(((String) item.message().content().get("text")).strip())));
            assertTrue(responses.stream().anyMatch(item ->
                    "error".equals(item.message().header().messageType())));
            var reply = responses.stream()
                    .filter(item -> "execute_reply".equals(item.message().header().messageType()))
                    .findFirst().orElseThrow();
            assertEquals("error", reply.message().content().get("status"));
        }
    }

    @Test
    void executeCanReadFrontendInputThroughStdinChannel() throws Exception {
        try (var handler = new JavaLandKernelMessageHandler()) {
            var execute = inboundWithId("stdin-execute", JupyterZmqChannel.SHELL,
                    "execute_request", Map.of(
                            "code", "new java.util.Scanner(System.in).nextLine()",
                            "silent", false, "store_history", true), Map.of());
            java.util.concurrent.atomic.AtomicReference<List<JupyterOutboundMessage>> result =
                    new java.util.concurrent.atomic.AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    result.set(handler.handle(execute));
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            });
            worker.start();

            List<JupyterOutboundMessage> requests = List.of();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (requests.isEmpty() && System.nanoTime() < deadline) {
                requests = handler.drainAsyncMessages();
                if (requests.isEmpty()) {
                    Thread.sleep(10);
                }
            }
            assertEquals(1, requests.size(), "execution should publish one input_request");
            JupyterOutboundMessage inputRequest = requests.getFirst();
            assertEquals(JupyterZmqChannel.STDIN, inputRequest.channel());
            assertEquals("input_request", inputRequest.message().header().messageType());
            assertEquals("stdin-execute",
                    inputRequest.message().parentHeader().get("msg_id"));

            var inputReply = inboundWithId("stdin-reply", JupyterZmqChannel.STDIN,
                    "input_reply", Map.of("value", "hello from frontend"),
                    Map.of("msg_id", "stdin-execute"));
            assertTrue(handler.handle(inputReply).isEmpty());
            worker.join(5_000);
            assertFalse(worker.isAlive(), "execution should resume after input_reply");
            assertNotNull(result.get());
            var output = result.get().stream()
                    .filter(item -> "execute_result".equals(item.message().header().messageType()))
                    .findFirst().orElseThrow();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) output.message().content().get("data");
            assertEquals("hello from frontend", data.get("text/plain"));
        }
    }

    private static JupyterInboundMessage inboundWithId(
            String id, JupyterZmqChannel channel, String type,
            Map<String, Object> content, Map<String, Object> parent) {
        var header = new tech.kayys.javaland.protocol.jupyter.JupyterMessageHeader(
                id, "test-user", "adapter-session", Instant.now().toString(), type, "5.3");
        return new JupyterInboundMessage(channel, List.of(new byte[]{7}),
                new tech.kayys.javaland.protocol.jupyter.JupyterMessage(
                        header, parent, Map.of(), content));
    }

    private static JupyterInboundMessage inbound(String type, Map<String, Object> content) {
        return inbound(JupyterZmqChannel.SHELL, type, content);
    }

    private static JupyterInboundMessage inbound(
            JupyterZmqChannel channel, String type, Map<String, Object> content) {
        var header = new tech.kayys.javaland.protocol.jupyter.JupyterMessageHeader(
                "adapter-request", "test-user", "adapter-session", Instant.now().toString(),
                type, "5.3");
        return new JupyterInboundMessage(channel, List.of(new byte[]{7}),
                new tech.kayys.javaland.protocol.jupyter.JupyterMessage(
                        header, Map.of(), Map.of(), content));
    }
}
