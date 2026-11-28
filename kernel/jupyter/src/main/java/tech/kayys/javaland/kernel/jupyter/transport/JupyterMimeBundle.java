package tech.kayys.javaland.kernel.jupyter.transport;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Separates binary MIME values from JSON content using Jupyter's buffer_paths convention.
 *
 * <p>Each binary MIME value is removed from the JSON data object and represented by a
 * path into that object. The corresponding bytes are carried as ordered multipart
 * buffers by {@code JupyterMessage}; they are never JSON/base64 encoded.</p>
 */
public record JupyterMimeBundle(
        Map<String, Object> data,
        List<List<String>> bufferPaths,
        List<byte[]> buffers) {

    public JupyterMimeBundle {
        data = Map.copyOf(data);
        bufferPaths = bufferPaths.stream().map(List::copyOf).toList();
        List<byte[]> copies = new ArrayList<>(buffers.size());
        buffers.forEach(buffer -> copies.add(buffer.clone()));
        buffers = List.copyOf(copies);
    }

    @Override
    public List<byte[]> buffers() {
        return buffers.stream().map(byte[]::clone).toList();
    }

    /** Converts a MIME bundle containing JSON values and optional byte[]/ByteBuffer values. */
    public static JupyterMimeBundle from(Map<String, ?> source) {
        Objects.requireNonNull(source, "source must not be null");
        Map<String, Object> jsonData = new LinkedHashMap<>();
        List<List<String>> paths = new ArrayList<>();
        List<byte[]> binary = new ArrayList<>();
        source.forEach((mime, value) -> {
            Objects.requireNonNull(mime, "MIME type must not be null");
            Objects.requireNonNull(value, "MIME value must not be null");
            if (value instanceof byte[] bytes) {
                paths.add(List.of("data", mime));
                binary.add(bytes.clone());
            } else if (value instanceof ByteBuffer byteBuffer) {
                ByteBuffer view = byteBuffer.asReadOnlyBuffer();
                byte[] bytes = new byte[view.remaining()];
                view.get(bytes);
                paths.add(List.of("data", mime));
                binary.add(bytes);
            } else {
                jsonData.put(mime, value);
            }
        });
        return new JupyterMimeBundle(jsonData, paths, binary);
    }
}
