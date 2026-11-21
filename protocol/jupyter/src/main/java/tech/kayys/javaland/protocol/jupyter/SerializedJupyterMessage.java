package tech.kayys.javaland.protocol.jupyter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The signed JSON frames of a Jupyter message, ready to be placed after the
 * transport's identity frames and the {@code <IDS|MSG>} delimiter.
 */
public record SerializedJupyterMessage(
        byte[] signature,
        byte[] header,
        byte[] parentHeader,
        byte[] metadata,
        byte[] content,
        List<byte[]> buffers) {

    public SerializedJupyterMessage {
        signature = copy(signature);
        header = copy(header);
        parentHeader = copy(parentHeader);
        metadata = copy(metadata);
        content = copy(content);
        Objects.requireNonNull(buffers, "buffers must not be null");
        List<byte[]> copied = new ArrayList<>(buffers.size());
        buffers.forEach(buffer -> copied.add(copy(buffer)));
        buffers = List.copyOf(copied);
    }

    @Override public byte[] signature() { return copy(signature); }
    @Override public byte[] header() { return copy(header); }
    @Override public byte[] parentHeader() { return copy(parentHeader); }
    @Override public byte[] metadata() { return copy(metadata); }
    @Override public byte[] content() { return copy(content); }

    @Override
    public List<byte[]> buffers() {
        return buffers.stream().map(SerializedJupyterMessage::copy).toList();
    }

    private static byte[] copy(byte[] bytes) {
        return Objects.requireNonNull(bytes, "frame must not be null").clone();
    }
}
