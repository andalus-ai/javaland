package tech.kayys.javaland.kernel.jupyter.transport;

import tech.kayys.javaland.protocol.jupyter.*;
import tech.kayys.javaland.transport.jupyter.zmq.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Protocol-aware event loop, independent of JShell and legacy JJava dispatch.
 *
 * <p>Shell requests are handled on a bounded, serial worker. When its pending slot is
 * full, the Shell socket is left unread so ZeroMQ provides backpressure while the
 * coordinator remains responsive to Control-channel interrupts and heartbeats.</p>
 */
public final class JupyterKernelCoordinator implements AutoCloseable {
    private static final long POLL_INTERVAL_MILLIS = 50;
    private static final int COMPLETED_RESPONSE_CAPACITY = 2;
    private final JupyterZmqTransport transport;
    private final JupyterConnectionConfig config;
    private final JupyterMessageCodec codec;
    private final JupyterMessageHandler handler;
    private final boolean closeTransportOnClose;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BlockingQueue<List<JupyterOutboundMessage>> completedResponses =
            new ArrayBlockingQueue<>(COMPLETED_RESPONSE_CAPACITY);
    private final AtomicReference<Exception> workerFailure = new AtomicReference<>();
    private final JupyterShellRequestScheduler shellWorker = new JupyterShellRequestScheduler();

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
        if (closed.get()) {
            throw new IllegalStateException("Jupyter coordinator is closed");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Jupyter coordinator is already running");
        }
        try {
            while (running.get() && !closed.get()) {
                checkWorkerFailure();
                sendCompletedResponses();
                for (JupyterZmqChannel channel : transport.poll(POLL_INTERVAL_MILLIS)) {
                    if (!running.get() || closed.get()) break;
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
            if (!closed.get()) {
                checkWorkerFailure();
                sendCompletedResponses();
            }
        } finally {
            running.set(false);
        }
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    private void dispatchShell() throws Exception {
        // Do not receive from the ROUTER socket when saturated. This preserves every
        // request in ZeroMQ's inbound queue and leaves the coordinator free for Control.
        if (!shellWorker.hasCapacity()) {
            return;
        }
        JupyterWireMessage wire = transport.receive(JupyterZmqChannel.SHELL);
        JupyterMessage request = codec.decode(wire.message(), config);
        JupyterInboundMessage inbound =
                new JupyterInboundMessage(JupyterZmqChannel.SHELL, wire.identities(), request);
        shellWorker.submit(() -> {
            if (closed.get()) {
                return;
            }
            try {
                List<JupyterOutboundMessage> responses = handler.handle(inbound);
                if (responses == null) {
                    throw new IllegalStateException("Message handler returned null");
                }
                if (!closed.get() && !completedResponses.offer(List.copyOf(responses))) {
                    throw new IllegalStateException("Jupyter completed-response queue is full");
                }
            } catch (Exception failure) {
                if (!closed.get()) {
                    workerFailure.compareAndSet(null, failure);
                    shellWorker.complete();
                }
            }
        });
    }

    private void dispatchOnCoordinator(JupyterZmqChannel channel) throws Exception {
        JupyterWireMessage wire = transport.receive(channel);
        JupyterMessage request = codec.decode(wire.message(), config);
        List<JupyterOutboundMessage> responses = handler.handle(
                new JupyterInboundMessage(channel, wire.identities(), request));
        if (responses == null) {
            throw new IllegalStateException("Message handler returned null");
        }
        sendResponses(responses);
    }

    private void sendCompletedResponses() {
        List<JupyterOutboundMessage> responses;
        while (!closed.get() && (responses = completedResponses.poll()) != null) {
            try {
                sendResponses(responses);
            } finally {
                shellWorker.complete();
            }
        }
    }

    private void sendResponses(List<JupyterOutboundMessage> responses) {
        if (closed.get()) {
            return;
        }
        for (JupyterOutboundMessage response : responses) {
            if (closed.get()) {
                return;
            }
            transport.send(response.channel(), new JupyterWireMessage(
                    response.identities(), codec.encode(response.message(), config)));
        }
    }

    private void checkWorkerFailure() throws Exception {
        Exception failure = workerFailure.getAndSet(null);
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            stop();
            shellWorker.close();
            completedResponses.clear();
            try {
                if (handler instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to close Jupyter message handler", failure);
            } finally {
                if (closeTransportOnClose) {
                    transport.close();
                }
            }
        }
    }
}
