package tech.kayys.javaland.transport.jupyter.zmq;

import org.junit.jupiter.api.Test;
import tech.kayys.javaland.protocol.jupyter.SerializedJupyterMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JupyterMultipartCodecTest {
    private final JupyterMultipartCodec codec = new JupyterMultipartCodec();
    @Test void preservesIdentitiesSignedFramesAndBuffers() {
        var payload = new SerializedJupyterMessage(b("sig"), b("h"), b("p"), b("m"), b("c"),
                List.of(b("buffer-a"), b("buffer-b")));
        var original = new JupyterWireMessage(List.of(b("route"), b("hop")), payload);
        var decoded = codec.decode(codec.encode(original));
        assertEquals(List.of("route", "hop"), strings(decoded.identities()));
        assertArrayEquals(b("sig"), decoded.message().signature());
        assertArrayEquals(b("h"), decoded.message().header());
        assertArrayEquals(b("p"), decoded.message().parentHeader());
        assertArrayEquals(b("m"), decoded.message().metadata());
        assertArrayEquals(b("c"), decoded.message().content());
        assertEquals(List.of("buffer-a", "buffer-b"), strings(decoded.message().buffers()));
    }
    @Test void permitsNoRoutingIdentityFrames() {
        var message = new JupyterWireMessage(List.of(),
                new SerializedJupyterMessage(b("s"), b("h"), b("p"), b("m"), b("c"), List.of()));
        assertTrue(codec.decode(codec.encode(message)).identities().isEmpty());
    }
    @Test void rejectsMissingDelimiter() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(List.of(b("identity"))));
    }
    @Test void rejectsIncompletePayload() {
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(List.of(b("<IDS|MSG>"), b("signature"), b("header"))));
    }
    @Test void rejectsDuplicateDelimiters() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(List.of(
                b("<IDS|MSG>"), b("<IDS|MSG>"), b("s"), b("h"), b("p"), b("m"), b("c"))));
    }
    private static byte[] b(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static List<String> strings(List<byte[]> values) {
        return values.stream().map(v -> new String(v, StandardCharsets.UTF_8)).toList();
    }
}
