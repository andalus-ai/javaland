package tech.kayys.javaland.protocol.jupyter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JupyterConnectionConfigTest {
    @Test
    void parsesStandardConnectionFileShape() {
        String json = """
                {
                  "transport": "tcp",
                  "ip": "127.0.0.1",
                  "shell_port": 5555,
                  "iopub_port": 5556,
                  "stdin_port": 5557,
                  "control_port": 5558,
                  "hb_port": 5559,
                  "key": "do-not-log-me",
                  "signature_scheme": "hmac-sha256"
                }
                """;

        JupyterConnectionConfig config = JupyterConnectionConfig.fromJson(json);

        assertEquals("tcp", config.transport());
        assertEquals(5555, config.shellPort());
        assertEquals(5559, config.heartbeatPort());
        assertFalse(config.toString().contains("do-not-log-me"));
    }

    @Test
    void acceptsSupportedConnectionTransports() {
        assertEquals("ipc", new JupyterConnectionConfig(
                "IPC", "/tmp/jupyter-kernel", 5555, 5556, 5557, 5558, 5559,
                "key", "hmac-sha256").transport());
    }

    @Test
    void rejectsInvalidPorts() {
        assertThrows(IllegalArgumentException.class, () ->
                new JupyterConnectionConfig(
                        "tcp", "127.0.0.1", 0, 5556, 5557, 5558, 5559,
                        "key", "hmac-sha256"));
    }

    @Test
    void rejectsUnsupportedTransportAndSignatureScheme() {
        assertThrows(IllegalArgumentException.class, () ->
                new JupyterConnectionConfig(
                        "inproc", "127.0.0.1", 5555, 5556, 5557, 5558, 5559,
                        "key", "hmac-sha256"));
        assertThrows(IllegalArgumentException.class, () ->
                new JupyterConnectionConfig(
                        "tcp", "127.0.0.1", 5555, 5556, 5557, 5558, 5559,
                        "key", "hmac-md5"));
    }

    @Test
    void rejectsMalformedConnectionFile() {
        assertThrows(IllegalArgumentException.class,
                () -> JupyterConnectionConfig.fromJson("{ invalid"));
    }
}
