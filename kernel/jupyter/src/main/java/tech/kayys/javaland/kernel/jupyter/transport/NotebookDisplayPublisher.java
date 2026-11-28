package tech.kayys.javaland.kernel.jupyter.transport;

import tech.kayys.javaland.protocol.jupyter.JupyterMessage;
import tech.kayys.javaland.protocol.jupyter.JupyterMessageHeader;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds Jupyter IOPub display lifecycle messages for a single kernel session.
 *
 * <p>Display identifiers are created by {@link #display} and can subsequently be
 * updated with {@link #updateDisplay}. Clearing output invalidates identifiers
 * created by this publisher, preventing updates to displays that no longer exist.</p>
 */
public final class NotebookDisplayPublisher {
    private static final String PROTOCOL_VERSION = "5.3";

    private final Set<String> activeDisplayIds = ConcurrentHashMap.newKeySet();

    /** Publishes a rich display and returns its stable display identifier. */
    public PublishedDisplay display(
            JupyterInboundMessage parent,
            Map<String, Object> data,
            Map<String, Object> metadata) {
        Objects.requireNonNull(parent, "parent must not be null");
        String displayId = UUID.randomUUID().toString();
        activeDisplayIds.add(displayId);
        Map<String, Object> transientMetadata = Map.of("display_id", displayId);
        DisplayPayload payload = displayContent(data, metadata, transientMetadata);
        return new PublishedDisplay(displayId,
                iopub(parent, "display_data", payload.content(), payload.buffers()));
    }

    /** Publishes a display using an identifier allocated by the execution bridge. */
    public PublishedDisplay display(
            JupyterInboundMessage parent,
            String displayId,
            Map<String, Object> data,
            Map<String, Object> metadata) {
        Objects.requireNonNull(parent, "parent must not be null");
        if (displayId == null || displayId.isBlank()) {
            throw new IllegalArgumentException("displayId must not be blank");
        }
        activeDisplayIds.add(displayId);
        Map<String, Object> transientMetadata = Map.of("display_id", displayId);
        DisplayPayload payload = displayContent(data, metadata, transientMetadata);
        return new PublishedDisplay(displayId,
                iopub(parent, "display_data", payload.content(), payload.buffers()));
    }

    /** Updates a display previously created by this publisher. */
    public JupyterOutboundMessage updateDisplay(
            JupyterInboundMessage parent,
            String displayId,
            Map<String, Object> data,
            Map<String, Object> metadata) {
        Objects.requireNonNull(parent, "parent must not be null");
        if (displayId == null || !activeDisplayIds.contains(displayId)) {
            throw new IllegalArgumentException("Unknown or cleared display_id: " + displayId);
        }
        DisplayPayload payload = displayContent(data, metadata, Map.of("display_id", displayId));
        return iopub(parent, "update_display_data", payload.content(), payload.buffers());
    }

    /** Emits clear_output and invalidates all display identifiers owned by this publisher. */
    public JupyterOutboundMessage clearOutput(JupyterInboundMessage parent, boolean wait) {
        Objects.requireNonNull(parent, "parent must not be null");
        activeDisplayIds.clear();
        return iopub(parent, "clear_output", Map.of("wait", wait));
    }

    private static DisplayPayload displayContent(
            Map<String, Object> data,
            Map<String, Object> metadata,
            Map<String, Object> transientMetadata) {
        JupyterMimeBundle bundle = JupyterMimeBundle.from(data);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("data", bundle.data());
        content.put("metadata", Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null")));
        content.put("transient", transientMetadata);
        if (!bundle.bufferPaths().isEmpty()) {
            content.put("buffer_paths", bundle.bufferPaths());
        }
        return new DisplayPayload(Map.copyOf(content), bundle.buffers());
    }

    private record DisplayPayload(Map<String, Object> content, List<byte[]> buffers) { }

    private static JupyterOutboundMessage iopub(
            JupyterInboundMessage parent, String type, Map<String, Object> content) {
        return iopub(parent, type, content, List.of());
    }

    private static JupyterOutboundMessage iopub(
            JupyterInboundMessage parent, String type, Map<String, Object> content,
            List<byte[]> buffers) {
        JupyterMessageHeader request = parent.message().header();
        JupyterMessageHeader header = new JupyterMessageHeader(
                UUID.randomUUID().toString(), request.username(), request.session(),
                Instant.now().toString(), type, PROTOCOL_VERSION);
        Map<String, Object> parentHeader = Map.of(
                "msg_id", request.messageId(),
                "username", request.username(),
                "session", request.session(),
                "date", request.date(),
                "msg_type", request.messageType(),
                "version", request.version());
        String topic = type + "." + request.session();
        return new JupyterOutboundMessage(
                JupyterZmqChannel.IOPUB,
                List.of(topic.getBytes(StandardCharsets.UTF_8)),
                new JupyterMessage(header, parentHeader, Map.of(), content, buffers));
    }

    public record PublishedDisplay(String displayId, JupyterOutboundMessage message) {
        public PublishedDisplay {
            Objects.requireNonNull(displayId, "displayId must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }
    }
}
