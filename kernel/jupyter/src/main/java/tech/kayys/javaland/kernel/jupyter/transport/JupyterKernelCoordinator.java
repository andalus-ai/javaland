package tech.kayys.javaland.kernel.jupyter.transport;

import tech.kayys.javaland.protocol.jupyter.*;
import tech.kayys.javaland.transport.jupyter.zmq.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Protocol-aware event loop, independent of JShell and legacy JJava dispatch. */
public final class JupyterKernelCoordinator implements AutoCloseable {
    private static final long POLL_INTERVAL_MILLIS = 100;
    private final JupyterZmqTransport transport;
    private final JupyterConnectionConfig config;
    private final JupyterMessageCodec codec;
    private final JupyterMessageHandler handler;
    private final boolean closeTransportOnClose;
    private final AtomicBoolean running = new AtomicBoolean();

    public JupyterKernelCoordinator(JupyterZmqTransport transport,
            JupyterConnectionConfig config, JupyterMessageHandler handler) {
        this(transport, config, new JupyterMessageCodec(), handler, false);
    }

    public JupyterKernelCoordinator(JupyterZmqTransport transport,
            JupyterConnectionConfig config, JupyterMessageCodec codec,
            JupyterMessageHandler handler, boolean closeTransportOnClose) {
        this.transport = Objects.requireNonNull(transport);
        this.config = Objects.requireNonNull(config);
        this.codec = Objects.requireNonNull(codec);
        this.handler = Objects.requireNonNull(handler);
        this.closeTransportOnClose = closeTransportOnClose;
    }

    /** Runs on the calling thread; bounded polling permits prompt cooperative shutdown. */
    public void run() throws Exception {
        if (!running.compareAndSet(false, true))
            throw new IllegalStateException("Jupyter coordinator is already running");
        try {
            while (running.get()) {
                for (JupyterZmqChannel channel : transport.poll(POLL_INTERVAL_MILLIS)) {
                    if (!running.get()) break;
                    if (channel == JupyterZmqChannel.HEARTBEAT) {
                        transport.replyHeartbeat(transport.receiveHeartbeat());
                    } else if (channel != JupyterZmqChannel.IOPUB) {
                        dispatch(channel);
                    }
                }
            }
        } finally {
            running.set(false);
        }
    }

    public void stop() { running.set(false); }
    public boolean isRunning() { return running.get(); }

    private void dispatch(JupyterZmqChannel channel) throws Exception {
        JupyterWireMessage wire = transport.receive(channel);
        JupyterMessage request = codec.decode(wire.message(), config);
        List<JupyterOutboundMessage> responses =
                handler.handle(new JupyterInboundMessage(channel, wire.identities(), request));
        if (responses == null) throw new IllegalStateException("Message handler returned null");
        for (JupyterOutboundMessage response : responses) {
            transport.send(response.channel(), new JupyterWireMessage(
                    response.identities(), codec.encode(response.message(), config)));
        }
    }

    @Override public void close() {
        stop();
        if (closeTransportOnClose) transport.close();
    }
}
