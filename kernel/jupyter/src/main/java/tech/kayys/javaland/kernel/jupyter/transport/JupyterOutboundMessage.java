package tech.kayys.javaland.kernel.jupyter.transport;

import tech.kayys.javaland.protocol.jupyter.JupyterMessage;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;
import java.util.List;
import java.util.Objects;

/** Outbound message with pre-delimiter envelope frames: ROUTER identities or a PUB/SUB topic. */
public record JupyterOutboundMessage(JupyterZmqChannel channel, List<byte[]> identities,
                                     JupyterMessage message) {
    public JupyterOutboundMessage {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(identities);
        if (channel == JupyterZmqChannel.HEARTBEAT)
            throw new IllegalArgumentException("Heartbeat uses raw transport frames");
        identities = identities.stream().map(value -> Objects.requireNonNull(value).clone()).toList();
        Objects.requireNonNull(message);
    }
    @Override public List<byte[]> identities() {
        return identities.stream().map(byte[]::clone).toList();
    }
}
