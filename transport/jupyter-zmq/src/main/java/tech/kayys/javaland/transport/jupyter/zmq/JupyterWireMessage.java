package tech.kayys.javaland.transport.jupyter.zmq;

import tech.kayys.javaland.protocol.jupyter.SerializedJupyterMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A protocol message plus the ZeroMQ routing identities preceding its delimiter. */
public record JupyterWireMessage(List<byte[]> identities, SerializedJupyterMessage message) {
    public JupyterWireMessage {
        Objects.requireNonNull(identities, "identities must not be null");
        List<byte[]> copy = new ArrayList<>(identities.size());
        identities.forEach(id -> copy.add(Objects.requireNonNull(id, "identity must not be null").clone()));
        identities = List.copyOf(copy);
        Objects.requireNonNull(message, "message must not be null");
    }
    @Override public List<byte[]> identities() { return identities.stream().map(byte[]::clone).toList(); }
}
