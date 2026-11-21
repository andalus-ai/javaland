package tech.kayys.javaland.kernel.jupyter.transport;

import org.junit.jupiter.api.Test;
import tech.kayys.javaland.protocol.jupyter.JupyterMessage;
import tech.kayys.javaland.protocol.jupyter.JupyterMessageHeader;
import tech.kayys.javaland.transport.jupyter.zmq.JupyterZmqChannel;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JupyterKernelCoordinatorTest {
    private JupyterMessage message(String type) {
        return new JupyterMessage(new JupyterMessageHeader(
                "id", "user", "session", "2026-11-21T00:00:00Z", type, "5.3"),
                Map.of(), Map.of(), Map.of());
    }

    @Test void inboundIdentitiesAreDefensivelyCopied() {
        byte[] identity = {1, 2, 3};
        var inbound = new JupyterInboundMessage(JupyterZmqChannel.SHELL,
                List.of(identity), message("kernel_info_request"));
        identity[0] = 9;
        byte[] exposed = inbound.identities().getFirst();
        exposed[0] = 8;
        assertArrayEquals(new byte[]{1, 2, 3}, inbound.identities().getFirst());
    }

    @Test void outboundCannotTargetHeartbeat() {
        assertThrows(IllegalArgumentException.class, () -> new JupyterOutboundMessage(
                JupyterZmqChannel.HEARTBEAT, List.of(), message("status")));
    }
}
