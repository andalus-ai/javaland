package tech.kayys.javaland.transport.jupyter.zmq;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import tech.kayys.javaland.protocol.jupyter.JupyterConnectionConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JupyterZmqTransport implements AutoCloseable {
    private final ZContext context;
    private final Map<JupyterZmqChannel, ZMQ.Socket> sockets = new EnumMap<>(JupyterZmqChannel.class);
    private final JupyterMultipartCodec codec = new JupyterMultipartCodec();
    private JupyterZmqPoller poller;
    private boolean closed;

    private JupyterZmqTransport(JupyterConnectionConfig config) {
        context = new ZContext();
        context.setLinger(0);
        try {
            for (JupyterZmqChannel channel : JupyterZmqChannel.values()) {
                ZMQ.Socket socket = context.createSocket(channel.socketType());
                socket.setLinger(0);
                String endpoint = endpoint(config, port(config, channel));
                if (!socket.bind(endpoint)) {
                    socket.close();
                    throw new IllegalStateException("Unable to bind " + channel + " at " + endpoint);
                }
                sockets.put(channel, socket);
            }
            poller = new JupyterZmqPoller(context, sockets);
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    public static JupyterZmqTransport bind(JupyterConnectionConfig config) {
        return new JupyterZmqTransport(Objects.requireNonNull(config, "config must not be null"));
    }

    /** Waits for inbound activity and returns every channel readable in this poll cycle. */
    public Set<JupyterZmqChannel> poll(long timeoutMillis) {
        ensureOpen();
        return poller.poll(timeoutMillis);
    }

    public JupyterWireMessage receive(JupyterZmqChannel channel) {
        ensureOpen();
        if (channel == JupyterZmqChannel.HEARTBEAT)
            throw new IllegalArgumentException("Use receiveHeartbeat for the heartbeat channel");
        return codec.decode(receiveFrames(socket(channel)));
    }

    public void send(JupyterZmqChannel channel, JupyterWireMessage message) {
        ensureOpen();
        if (channel == JupyterZmqChannel.HEARTBEAT)
            throw new IllegalArgumentException("Heartbeat uses raw echo frames");
        List<byte[]> frames = codec.encode(message);
        ZMQ.Socket socket = socket(channel);
        for (int i = 0; i < frames.size(); i++) {
            boolean sent = i == frames.size() - 1
                    ? socket.send(frames.get(i), 0) : socket.sendMore(frames.get(i));
            if (!sent) throw new IllegalStateException("Failed sending frame " + i + " on " + channel);
        }
    }

    public byte[] receiveHeartbeat() {
        ensureOpen();
        byte[] request = socket(JupyterZmqChannel.HEARTBEAT).recv(0);
        if (request == null) throw new IllegalStateException("Heartbeat receive failed");
        return request.clone();
    }

    public void replyHeartbeat(byte[] request) {
        ensureOpen();
        Objects.requireNonNull(request, "request must not be null");
        if (!socket(JupyterZmqChannel.HEARTBEAT).send(request.clone(), 0))
            throw new IllegalStateException("Heartbeat reply failed");
    }

    public Map<JupyterZmqChannel, String> endpoints(JupyterConnectionConfig config) {
        EnumMap<JupyterZmqChannel, String> result = new EnumMap<>(JupyterZmqChannel.class);
        for (JupyterZmqChannel channel : JupyterZmqChannel.values())
            result.put(channel, endpoint(config, port(config, channel)));
        return Map.copyOf(result);
    }

    @Override public void close() {
        if (!closed) {
            closed = true;
            if (poller != null) poller.close();
            context.close();
            sockets.clear();
        }
    }

    private List<byte[]> receiveFrames(ZMQ.Socket socket) {
        List<byte[]> frames = new ArrayList<>();
        byte[] frame = socket.recv(0);
        if (frame == null) throw new IllegalStateException("ZeroMQ receive failed");
        frames.add(frame);
        while (socket.hasReceiveMore()) {
            frame = socket.recv(0);
            if (frame == null) throw new IllegalStateException("ZeroMQ multipart receive failed");
            frames.add(frame);
        }
        return frames;
    }

    private ZMQ.Socket socket(JupyterZmqChannel channel) {
        return Objects.requireNonNull(sockets.get(channel), "Missing channel socket: " + channel);
    }
    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Jupyter ZeroMQ transport is closed");
    }
    private static int port(JupyterConnectionConfig c, JupyterZmqChannel channel) {
        return switch (channel) {
            case SHELL -> c.shellPort(); case IOPUB -> c.iopubPort();
            case STDIN -> c.stdinPort(); case CONTROL -> c.controlPort();
            case HEARTBEAT -> c.heartbeatPort();
        };
    }
    private static String endpoint(JupyterConnectionConfig c, int port) {
        return switch (c.transport()) {
            case "tcp" -> "tcp://" + c.ip() + ":" + port;
            case "ipc" -> "ipc://" + c.ip() + "-" + port;
            default -> throw new IllegalArgumentException("Unsupported transport: " + c.transport());
        };
    }
}
