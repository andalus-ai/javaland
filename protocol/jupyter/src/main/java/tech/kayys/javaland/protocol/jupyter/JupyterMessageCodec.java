package tech.kayys.javaland.protocol.jupyter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encodes and decodes the signed JSON portion of Jupyter wire messages.
 *
 * <p>ZeroMQ identity frames and the {@code <IDS|MSG>} delimiter belong to the
 * transport layer and are intentionally not handled here.</p>
 */
public final class JupyterMessageCodec {
    private static final TypeReference<Map<String, Object>> OBJECT =
            new TypeReference<>() { };

    private final ObjectMapper mapper;

    public JupyterMessageCodec() {
        this(new ObjectMapper());
    }

    public JupyterMessageCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null").copy();
    }

    public SerializedJupyterMessage encode(JupyterMessage message, JupyterConnectionConfig config) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(config, "config must not be null");
        try {
            byte[] header = mapper.writeValueAsBytes(message.header());
            byte[] parentHeader = mapper.writeValueAsBytes(message.parentHeader());
            byte[] metadata = mapper.writeValueAsBytes(message.metadata());
            byte[] content = mapper.writeValueAsBytes(message.content());
            byte[] signature = JupyterSignatures.sign(config.signatureScheme(), config.key(),
                    header, parentHeader, metadata, content);
            return new SerializedJupyterMessage(
                    signature, header, parentHeader, metadata, content, message.buffers());
        } catch (IOException failure) {
            throw new IllegalArgumentException("Unable to encode Jupyter message", failure);
        }
    }

    public JupyterMessage decode(
            SerializedJupyterMessage serialized,
            JupyterConnectionConfig config) {
        Objects.requireNonNull(serialized, "serialized must not be null");
        Objects.requireNonNull(config, "config must not be null");
        byte[] header = serialized.header();
        byte[] parentHeader = serialized.parentHeader();
        byte[] metadata = serialized.metadata();
        byte[] content = serialized.content();

        if (!JupyterSignatures.verify(config.signatureScheme(), config.key(),
                serialized.signature(), header, parentHeader, metadata, content)) {
            throw new SecurityException("Jupyter message signature verification failed");
        }

        try {
            JupyterMessageHeader parsedHeader =
                    mapper.readValue(header, JupyterMessageHeader.class);
            return new JupyterMessage(
                    parsedHeader,
                    mapper.readValue(parentHeader, OBJECT),
                    mapper.readValue(metadata, OBJECT),
                    mapper.readValue(content, OBJECT),
                    serialized.buffers());
        } catch (IOException failure) {
            throw new IllegalArgumentException("Unable to decode Jupyter message", failure);
        }
    }
}
