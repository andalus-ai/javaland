package tech.kayys.javaland.kernel.jupyter.transport;

import org.junit.jupiter.api.Test;
import tech.kayys.javaland.protocol.jupyter.JupyterMessage;
import tech.kayys.javaland.protocol.jupyter.JupyterMessageHeader;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotebookDisplayPublisherTest {
    @Test
    void displayCreatesIopubDisplayDataWithTransientIdentifier() {
        NotebookDisplayPublisher publisher = new NotebookDisplayPublisher();

        var published = publisher.display(inbound(), Map.of(
                "text/plain", "hello", "text/html", "<b>hello</b>"), Map.of());

        assertFalse(published.displayId().isBlank());
        assertEquals(JupyterZmqChannel.IOPUB, published.message().channel());
        assertEquals("display_data", published.message().message().header().messageType());
        assertEquals("display_data.test-session",
                new String(published.message().identities().getFirst(),
                        java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(published.displayId(), transientMetadata(published.message()).get("display_id"));
        assertEquals("hello", data(published.message()).get("text/plain"));
        assertEquals("display-request",
                published.message().message().parentHeader().get("msg_id"));
    }

    @Test
    void updateDisplayReusesIdentifierAndPublishesUpdateDisplayData() {
        NotebookDisplayPublisher publisher = new NotebookDisplayPublisher();
        var published = publisher.display(inbound(), Map.of("text/plain", "before"), Map.of());

        var update = publisher.updateDisplay(inbound(), published.displayId(),
                Map.of("text/plain", "after", "text/html", "<i>after</i>"), Map.of());

        assertEquals("update_display_data", update.message().header().messageType());
        assertEquals(published.displayId(), transientMetadata(update).get("display_id"));
        assertEquals("after", data(update).get("text/plain"));
    }

    @Test
    void clearOutputCarriesWaitFlagAndInvalidatesExistingDisplayIds() {
        NotebookDisplayPublisher publisher = new NotebookDisplayPublisher();
        var published = publisher.display(inbound(), Map.of("text/plain", "before"), Map.of());

        var clear = publisher.clearOutput(inbound(), true);

        assertEquals("clear_output", clear.message().header().messageType());
        assertEquals(Map.of("wait", true), clear.message().message().content());
        assertThrows(IllegalArgumentException.class, () -> publisher.updateDisplay(
                inbound(), published.displayId(), Map.of("text/plain", "after"), Map.of()));
    }

    @Test
    void rejectsUnknownDisplayIdentifiers() {
        NotebookDisplayPublisher publisher = new NotebookDisplayPublisher();

        assertThrows(IllegalArgumentException.class, () -> publisher.updateDisplay(
                inbound(), "not-registered", Map.of("text/plain", "after"), Map.of()));
    }

    @Test
    void displayCarriesBinaryMimeValuesInSeparateMessageBuffers() {
        NotebookDisplayPublisher publisher = new NotebookDisplayPublisher();
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};

        var published = publisher.display(inbound(), Map.of(
                "text/plain", "PNG image",
                "image/png", png), Map.of());

        assertEquals(Map.of("text/plain", "PNG image"),
                data(published.message()));
        assertEquals(List.of(List.of("data", "image/png")),
                published.message().message().content().get("buffer_paths"));
        assertArrayEquals(png, published.message().message().buffers().getFirst());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(JupyterOutboundMessage message) {
        return (Map<String, Object>) message.message().content().get("data");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> transientMetadata(JupyterOutboundMessage message) {
        return (Map<String, Object>) message.message().content().get("transient");
    }

    private static JupyterInboundMessage inbound() {
        var header = new JupyterMessageHeader(
                "display-request", "test-user", "test-session",
                Instant.now().toString(), "execute_request", "5.3");
        return new JupyterInboundMessage(JupyterZmqChannel.SHELL, List.of(new byte[]{1}),
                new JupyterMessage(header, Map.of(), Map.of(), Map.of()));
    }
}
