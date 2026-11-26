package tech.kayys.javaland.transport.jupyter.zmq;

import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import tech.kayys.javaland.protocol.jupyter.JupyterConnectionConfig;
import tech.kayys.javaland.protocol.jupyter.SerializedJupyterMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JupyterZmqSocketInteropTest {
    private static final byte[] EMPTY = new byte[0];
    private final JupyterMultipartCodec codec = new JupyterMultipartCodec();

    @Test
    void dealerShellRoundTripPreservesRouterIdentityAndJupyterFrames() throws Exception {
        JupyterConnectionConfig config = connectionConfig();
        try (JupyterZmqTransport transport = JupyterZmqTransport.bind(config);
             ZContext clients = new ZContext()) {
            ZMQ.Socket dealer = clients.createSocket(SocketType.DEALER);
            dealer.setLinger(0);
            dealer.setReceiveTimeOut(2_000);
            assertTrue(dealer.connect(transport.endpoints(config).get(JupyterZmqChannel.SHELL)));
            try {
                JupyterWireMessage request = wire("request-signature", "execute_request", "request-content");
                sendFrames(dealer, codec.encode(request));

                assertTrue(transport.poll(2_000).contains(JupyterZmqChannel.SHELL));
                JupyterWireMessage inbound = transport.receive(JupyterZmqChannel.SHELL);
                assertEquals(1, inbound.identities().size());
                byte[] routerIdentity = inbound.identities().getFirst();
                assertTrue(routerIdentity.length > 0);
                assertEquals("execute_request", utf8(inbound.message().header()));

                transport.send(JupyterZmqChannel.SHELL,
                        new JupyterWireMessage(inbound.identities(),
                                serialized("reply-signature", "execute_reply", "reply-content")));

                JupyterWireMessage reply = codec.decode(receiveFrames(dealer));
                assertTrue(reply.identities().isEmpty(),
                        "DEALER must receive the payload without the ROUTER routing envelope");
                assertEquals("execute_reply", utf8(reply.message().header()));
                assertEquals("reply-content", utf8(reply.message().content()));
            } finally {
                dealer.close();
            }
        }
    }

    @Test
    void subscriberReceivesIopubTopicBeforeJupyterDelimiter() throws Exception {
        JupyterConnectionConfig config = connectionConfig();
        try (JupyterZmqTransport transport = JupyterZmqTransport.bind(config);
             ZContext clients = new ZContext()) {
            ZMQ.Socket subscriber = clients.createSocket(SocketType.SUB);
            subscriber.setLinger(0);
            subscriber.setReceiveTimeOut(2_000);
            byte[] topic = bytes("execute_result.session-1");
            subscriber.subscribe(topic);
            assertTrue(subscriber.connect(transport.endpoints(config).get(JupyterZmqChannel.IOPUB)));
            try {
                // Allow the SUB subscription to propagate to the PUB socket.
                Thread.sleep(150);
                transport.send(JupyterZmqChannel.IOPUB,
                        new JupyterWireMessage(List.of(topic),
                                serialized("iopub-signature", "execute_result", "display-data")));

                JupyterWireMessage received = codec.decode(receiveFrames(subscriber));
                assertEquals(1, received.identities().size());
                assertArrayEquals(topic, received.identities().getFirst());
                assertEquals("execute_result", utf8(received.message().header()));
                assertEquals("display-data", utf8(received.message().content()));
            } finally {
                subscriber.close();
            }
        }
    }

    private static JupyterConnectionConfig connectionConfig() throws IOException {
        int[] ports = new int[5];
        List<ServerSocket> reservations = new ArrayList<>();
        try {
            for (int i = 0; i < ports.length; i++) {
                ServerSocket reservation = new ServerSocket(0);
                reservations.add(reservation);
                ports[i] = reservation.getLocalPort();
            }
        } finally {
            for (ServerSocket reservation : reservations) reservation.close();
        }
        return new JupyterConnectionConfig("tcp", "127.0.0.1",
                ports[0], ports[1], ports[2], ports[3], ports[4], "", "hmac-sha256");
    }

    private static JupyterWireMessage wire(String signature, String header, String content) {
        return new JupyterWireMessage(List.of(), serialized(signature, header, content));
    }

    private static SerializedJupyterMessage serialized(String signature, String header, String content) {
        return new SerializedJupyterMessage(bytes(signature), bytes(header), EMPTY, EMPTY,
                bytes(content), List.of());
    }

    private static void sendFrames(ZMQ.Socket socket, List<byte[]> frames) {
        for (int i = 0; i < frames.size(); i++) {
            boolean sent = i == frames.size() - 1
                    ? socket.send(frames.get(i), 0) : socket.sendMore(frames.get(i));
            assertTrue(sent, "Failed to send multipart frame " + i);
        }
    }

    private static List<byte[]> receiveFrames(ZMQ.Socket socket) {
        List<byte[]> frames = new ArrayList<>();
        byte[] frame = socket.recv(0);
        assertNotNull(frame, "Timed out waiting for ZeroMQ message");
        frames.add(frame);
        while (socket.hasReceiveMore()) {
            frame = socket.recv(0);
            assertNotNull(frame, "Multipart message ended unexpectedly");
            frames.add(frame);
        }
        return frames;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String utf8(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
