package tech.kayys.javaland.protocol.jupyter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class JupyterMessageCodecTest {
    private static final JupyterConnectionConfig CONFIG = config("test-secret");

    @Test
    void roundTripsSignedMessageAndBinaryBuffers() {
        JupyterMessage message = message();
        JupyterMessageCodec codec = new JupyterMessageCodec();

        SerializedJupyterMessage encoded = codec.encode(message, CONFIG);
        JupyterMessage decoded = codec.decode(encoded, CONFIG);

        assertEquals(message.header(), decoded.header());
        assertEquals(message.parentHeader(), decoded.parentHeader());
        assertEquals(message.metadata(), decoded.metadata());
        assertEquals(message.content(), decoded.content());
        assertArrayEquals(new byte[] {1, 2, 3}, decoded.buffers().getFirst());
    }

    @Test
    void serializesStandardJupyterHeaderFieldNames() {
        SerializedJupyterMessage encoded =
                new JupyterMessageCodec().encode(message(), CONFIG);
        String json = new String(encoded.header(), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"msg_id\""));
        assertTrue(json.contains("\"msg_type\""));
        assertTrue(json.contains("\"username\""));
    }

    @Test
    void rejectsTamperedSignedFrame() {
        JupyterMessageCodec codec = new JupyterMessageCodec();
        SerializedJupyterMessage encoded = codec.encode(message(), CONFIG);
        byte[] changedContent = encoded.content();
        changedContent[0] ^= 1;

        SerializedJupyterMessage tampered = new SerializedJupyterMessage(
                encoded.signature(), encoded.header(), encoded.parentHeader(),
                encoded.metadata(), changedContent, encoded.buffers());

        assertThrows(SecurityException.class, () -> codec.decode(tampered, CONFIG));
    }

    @Test
    void supportsUnsignedMessagesWhenConnectionKeyIsEmpty() {
        JupyterConnectionConfig unsigned = config("");
        JupyterMessageCodec codec = new JupyterMessageCodec();
        SerializedJupyterMessage encoded = codec.encode(message(), unsigned);

        assertArrayEquals(new byte[0], encoded.signature());
        assertEquals(message().content(), codec.decode(encoded, unsigned).content());
    }

    @Test
    void rejectsIncorrectSigningKey() {
        JupyterMessageCodec codec = new JupyterMessageCodec();
        SerializedJupyterMessage encoded = codec.encode(message(), CONFIG);

        assertThrows(SecurityException.class,
                () -> codec.decode(encoded, config("different-secret")));
    }

    private static JupyterMessage message() {
        return new JupyterMessage(
                new JupyterMessageHeader(
                        "msg-1", "javaland", "session-1",
                        "2026-11-21T00:00:00Z", "execute_request", "5.3"),
                Map.of("msg_id", "parent-1", "msg_type", "execute_request"),
                Map.of("silent", false),
                Map.of("code", "1 + 1", "execution_count", 1),
                List.of(new byte[] {1, 2, 3}));
    }

    private static JupyterConnectionConfig config(String key) {
        return new JupyterConnectionConfig(
                "tcp", "127.0.0.1", 5555, 5556, 5557, 5558, 5559,
                key, "hmac-sha256");
    }
}
