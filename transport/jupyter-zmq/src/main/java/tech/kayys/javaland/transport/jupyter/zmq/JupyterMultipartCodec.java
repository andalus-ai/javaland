package tech.kayys.javaland.transport.jupyter.zmq;

import tech.kayys.javaland.protocol.jupyter.SerializedJupyterMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Handles Jupyter multipart framing; socket operations are deliberately separate. */
public final class JupyterMultipartCodec {
    private static final byte[] DELIMITER = "<IDS|MSG>".getBytes(StandardCharsets.US_ASCII);
    private static final int SIGNED_FRAME_COUNT = 5;

    public List<byte[]> encode(JupyterWireMessage wire) {
        Objects.requireNonNull(wire, "wire must not be null");
        List<byte[]> result = new ArrayList<>();
        wire.identities().forEach(frame -> result.add(frame.clone()));
        result.add(DELIMITER.clone());
        SerializedJupyterMessage m = wire.message();
        result.add(m.signature()); result.add(m.header()); result.add(m.parentHeader());
        result.add(m.metadata()); result.add(m.content());
        m.buffers().forEach(buffer -> result.add(buffer.clone()));
        return List.copyOf(result);
    }

    public JupyterWireMessage decode(List<byte[]> frames) {
        Objects.requireNonNull(frames, "frames must not be null");
        int delimiter = -1;
        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i) != null && Arrays.equals(DELIMITER, frames.get(i))) {
                if (delimiter >= 0) throw new IllegalArgumentException("Multiple Jupyter message delimiters");
                delimiter = i;
            }
        }
        if (delimiter < 0) throw new IllegalArgumentException("Missing <IDS|MSG> delimiter");
        if (frames.size() - delimiter - 1 < SIGNED_FRAME_COUNT) {
            throw new IllegalArgumentException("Expected signature and four JSON frames after delimiter");
        }
        List<byte[]> identities = new ArrayList<>(delimiter);
        for (int i = 0; i < delimiter; i++) identities.add(copy(frames.get(i)));
        int p = delimiter + 1;
        List<byte[]> buffers = new ArrayList<>();
        for (int i = p + SIGNED_FRAME_COUNT; i < frames.size(); i++) buffers.add(copy(frames.get(i)));
        SerializedJupyterMessage message = new SerializedJupyterMessage(
                copy(frames.get(p)), copy(frames.get(p + 1)), copy(frames.get(p + 2)),
                copy(frames.get(p + 3)), copy(frames.get(p + 4)), buffers);
        return new JupyterWireMessage(identities, message);
    }
    private static byte[] copy(byte[] value) {
        return Objects.requireNonNull(value, "multipart frame must not be null").clone();
    }
}
