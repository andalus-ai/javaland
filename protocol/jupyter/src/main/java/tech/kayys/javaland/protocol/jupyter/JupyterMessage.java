package tech.kayys.javaland.protocol.jupyter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-level Jupyter message envelope.
 *
 * <p>Content and metadata remain generic JSON objects so protocol extensions do not
 * require changes to the transport model.</p>
 */
public record JupyterMessage(
        JupyterMessageHeader header,
        Map<String, Object> parentHeader,
        Map<String, Object> metadata,
        Map<String, Object> content,
        List<byte[]> buffers) {

    public JupyterMessage {
        Objects.requireNonNull(header, "header must not be null");
        parentHeader = immutableObject(parentHeader);
        metadata = immutableObject(metadata);
        content = immutableObject(content);
        buffers = copyBuffers(buffers);
    }

    public JupyterMessage(
            JupyterMessageHeader header,
            Map<String, Object> parentHeader,
            Map<String, Object> metadata,
            Map<String, Object> content) {
        this(header, parentHeader, metadata, content, List.of());
    }

    @Override
    public List<byte[]> buffers() {
        return copyBuffers(buffers);
    }

    private static Map<String, Object> immutableObject(Map<String, Object> value) {
        Objects.requireNonNull(value, "message object must not be null");
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static List<byte[]> copyBuffers(List<byte[]> values) {
        Objects.requireNonNull(values, "buffers must not be null");
        List<byte[]> copy = new ArrayList<>(values.size());
        for (byte[] value : values) {
            copy.add(Objects.requireNonNull(value, "buffer must not be null").clone());
        }
        return List.copyOf(copy);
    }
}
