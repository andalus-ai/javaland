package tech.kayys.javaland.kernel.jupyter.transport;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JupyterMimeBundleTest {
    @Test
    void extractsBinaryMimeValuesIntoOrderedBuffersAndPaths() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};
        byte[] wav = new byte[] {0x52, 0x49, 0x46, 0x46};
        var bundle = JupyterMimeBundle.from(Map.of(
                "text/plain", "preview",
                "image/png", png,
                "audio/wav", ByteBuffer.wrap(wav)));

        assertEquals(Map.of("text/plain", "preview"), bundle.data());
        assertEquals(java.util.List.of(
                java.util.List.of("data", "image/png"),
                java.util.List.of("data", "audio/wav")), bundle.bufferPaths());
        assertArrayEquals(png, bundle.buffers().get(0));
        assertArrayEquals(wav, bundle.buffers().get(1));

        png[0] = 0;
        assertEquals((byte) 0x89, bundle.buffers().get(0)[0]);
    }

    @Test
    void keepsJsonOnlyMimeBundlesFreeOfBufferMetadata() {
        var bundle = JupyterMimeBundle.from(Map.of(
                "text/plain", "hello",
                "application/json", Map.of("ok", true)));

        assertEquals(Map.of(
                "text/plain", "hello",
                "application/json", Map.of("ok", true)), bundle.data());
        assertTrue(bundle.bufferPaths().isEmpty());
        assertTrue(bundle.buffers().isEmpty());
    }
}
