package tech.kayys.javaland.kernel.jupyter.transport;

import tech.kayys.javaland.protocol.jupyter.*;
import tech.kayys.javaland.transport.jupyter.zmq.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Protocol-aware event loop, independent of JShell and legacy JJava dispatch.
 *
 * <p>Shell requests are handled on one dedicated worker to preserve request order.
 * The coordinator thread remains responsible for all ZeroMQ operations and continues
 * polling the Control channel while user code is executing.</p>
 */
public final class JupyterKernelCoordinator implements AutoCloseable {
    private static final long POLL_INTERVAL_MILLIS = 50;
    private final JupyterZmqTransport transport;
    private final JupyterConnectionConfig config;
    private final JupyterMessageCodec codec;
    private final JupyterMessageHandler handler;
    private final boolean closeTransportOnClose;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BlockingQueue<List<JupyterOutboundMessage>> completedResponses =
            new LinkedBlockingQueue<>();
    private final AtomicReference<Exception> workerFailure = new AtomicReference<>();
    private final ExecutorService shellWorker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "javaland-jupyter-shell");
        thread.setDaemon(true);
        return thread;
    });

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

    /** Runs on the calling thread; bounded polling keeps Control and heartbeat responsive. */
    public void run() throws Exception {
        if (!running.compareAndSet(false, true))
            throw new IllegalStateException("Jupyter coordinator is already running");
        try {
            while (running.get()) {
                checkWorkerFailure();
                sendCompletedResponses();
                for (JupyterZmqChannel channel : transport.poll(POLL_INTERVAL_MILLIS)) {
                    if (!running.get()) break;
                    if (channel == JupyterZmqChannel.HEARTBEAT) {
                        transport.replyHeartbeat(transport.receiveHeartbeat());
                    } else if (channel == JupyterZmqChannel.SHELL) {
                        dispatchShell();
                    } else if (channel == JupyterZmqChannel.CONTROL
                            || channel == JupyterZmqChannel.STDIN) {
                        dispatchOnCoordinator(channel);
                    }
                }
            }
            checkWorkerFailure();
            sendCompletedResponses();
        } finally {
            running.set(false);
        }
    }

    public void stop() { running.set(false); }
    public boolean isRunning() { return running.get(); }

    private void dispatchShell() throws Exception {
        JupyterWireMessage wire = transport.receive(JupyterZmqChannel.SHELL);
        JupyterMessage request = codec.decode(wire.message(), config);
        JupyterInboundMessage inbound =
                new JupyterInboundMessage(JupyterZmqChannel.SHELL, wire.identities(), request);
        shellWorker.execute(() -> {
            try {
                List<JupyterOutboundMessage> responses = handler.handle(inbound);
                if (responses == null)
                    throw new IllegalStateException("Message handler returned null");
                completedResponses.add(List.copyOf(responses));
            } catch (Exception failure) {
                workerFailure.compareAndSet(null, failure);
            }
        });
    }

    private void dispatchOnCoordinator(JupyterZmqChannel channel) throws Exception {
        JupyterWireMessage wire = transport.receive(channel);
        JupyterMessage request = codec.decode(wire.message(), config);
        List<JupyterOutboundMessage> responses = handler.handle(
                new JupyterInboundMessage(channel, wire.identities(), request));
        if (responses == null) throw new IllegalStateException("Message handler returned null");
        sendResponses(responses);
    }

    private void sendCompletedResponses() {
        List<JupyterOutboundMessage> responses;
        while ((responses = completedResponses.poll()) != null) sendResponses(responses);
    }

    private void sendResponses(List<JupyterOutboundMessage> responses) {
        for (JupyterOutboundMessage response : responses) {
            transport.send(response.channel(), new JupyterWireMessage(
                    response.identities(), codec.encode(response.message(), config)));
        }
    }

    private void checkWorkerFailure() throws Exception {
        Exception failure = workerFailure.getAndSet(null);
        if (failure != null) throw failure;
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            stop();
            shellWorker.shutdownNow();
            try {
                if (handler instanceof AutoCloseable closeable) closeable.close();
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to close Jupyter message handler", failure);
            } finally {
                if (closeTransportOnClose) transport.close();
            }
        }
    }
}
