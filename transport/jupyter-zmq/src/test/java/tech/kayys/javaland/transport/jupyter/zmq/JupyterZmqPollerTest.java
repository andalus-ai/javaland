package tech.kayys.javaland.transport.jupyter.zmq;

import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JupyterZmqPollerTest {
    @Test
    void reportsOnlyChannelsWithInboundMessages() {
        try (ZContext context = new ZContext()) {
            Map<JupyterZmqChannel, ZMQ.Socket> sockets = new EnumMap<>(JupyterZmqChannel.class);
            for (JupyterZmqChannel channel : JupyterZmqChannel.values()) {
                ZMQ.Socket socket = context.createSocket(channel.socketType());
                socket.setLinger(0);
                assertTrue(socket.bind("inproc://poller-" + channel.name().toLowerCase()));
                sockets.put(channel, socket);
            }
            ZMQ.Socket client = context.createSocket(SocketType.DEALER);
            client.setLinger(0);
            assertTrue(client.connect("inproc://poller-shell"));
            try (JupyterZmqPoller poller = new JupyterZmqPoller(context, sockets)) {
                assertTrue(client.send("request", 0));
                assertTrue(poller.poll(1_000).contains(JupyterZmqChannel.SHELL));
                assertFalse(poller.poll(0).contains(JupyterZmqChannel.IOPUB));
            } finally {
                client.close();
                sockets.values().forEach(ZMQ.Socket::close);
            }
        }
    }

    @Test
    void rejectsInvalidTimeoutAndUseAfterClose() {
        try (ZContext context = new ZContext()) {
            Map<JupyterZmqChannel, ZMQ.Socket> sockets = new EnumMap<>(JupyterZmqChannel.class);
            for (JupyterZmqChannel channel : JupyterZmqChannel.values()) {
                ZMQ.Socket socket = context.createSocket(channel.socketType());
                socket.setLinger(0);
                assertTrue(socket.bind("inproc://poller-close-" + channel.name().toLowerCase()));
                sockets.put(channel, socket);
            }
            JupyterZmqPoller poller = new JupyterZmqPoller(context, sockets);
            assertThrows(IllegalArgumentException.class, () -> poller.poll(-2));
            poller.close();
            assertThrows(IllegalStateException.class, () -> poller.poll(0));
            sockets.values().forEach(ZMQ.Socket::close);
        }
    }
}
