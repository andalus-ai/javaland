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
