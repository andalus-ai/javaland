package tech.kayys.javaland.protocol.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

/**
 * Connection-file settings defined by the Jupyter kernel protocol.
 *
 * <p>The key is secret material and is deliberately redacted from {@link #toString()}.</p>
 */
public record JupyterConnectionConfig(
        String transport,
        String ip,
        @JsonProperty("shell_port") int shellPort,
        @JsonProperty("iopub_port") int iopubPort,
        @JsonProperty("stdin_port") int stdinPort,
        @JsonProperty("control_port") int controlPort,
        @JsonProperty("hb_port") int heartbeatPort,
        String key,
        @JsonProperty("signature_scheme") String signatureScheme) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public JupyterConnectionConfig {
        transport = requireText(transport, "transport").toLowerCase(java.util.Locale.ROOT);
        ip = requireText(ip, "ip");
        key = Objects.requireNonNull(key, "key must not be null");
        signatureScheme = requireText(signatureScheme, "signature_scheme");
        validatePort(shellPort, "shell_port");
        validatePort(iopubPort, "iopub_port");
        validatePort(stdinPort, "stdin_port");
        validatePort(controlPort, "control_port");
        validatePort(heartbeatPort, "hb_port");
        if (!"tcp".equals(transport) && !"ipc".equals(transport)) {
            throw new IllegalArgumentException("Unsupported Jupyter transport: " + transport);
        }
        JupyterSignatures.algorithmFor(signatureScheme);
    }

    public static JupyterConnectionConfig fromJson(String json) {
        Objects.requireNonNull(json, "json must not be null");
        try {
            return JSON.readValue(json, JupyterConnectionConfig.class);
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof IllegalArgumentException illegalArgument) {
                throw illegalArgument;
            }
            throw new IllegalArgumentException("Invalid Jupyter connection file", failure);
        }
    }

    @Override
    public String toString() {
        return "JupyterConnectionConfig[transport=" + transport
                + ", ip=" + ip
                + ", shellPort=" + shellPort
                + ", iopubPort=" + iopubPort
                + ", stdinPort=" + stdinPort
                + ", controlPort=" + controlPort
                + ", heartbeatPort=" + heartbeatPort
                + ", key=<redacted>"
                + ", signatureScheme=" + signatureScheme + "]";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void validatePort(int port, String field) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(field + " must be between 1 and 65535");
        }
    }
}
