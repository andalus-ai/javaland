package tech.kayys.javaland.kernel.jupyter.transport;

import tech.kayys.javaland.protocol.jupyter.JupyterMessage;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;
import java.util.List;
import java.util.Objects;

/** Verified inbound message with its channel and immutable routing identities. */
public record JupyterInboundMessage(JupyterZmqChannel channel, List<byte[]> identities,
                                    JupyterMessage message) {
    public JupyterInboundMessage {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(identities);
        identities = identities.stream().map(value -> Objects.requireNonNull(value).clone()).toList();
        Objects.requireNonNull(message);
    }
    @Override public List<byte[]> identities() {
        return identities.stream().map(byte[]::clone).toList();
    }
}
