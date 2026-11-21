package tech.kayys.javaland.transport.jupyter.zmq;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Multiplexes inbound readiness across the five Jupyter channels.
 *
 * <p>Polling and socket reads must be performed by the same owning thread.
 * This class reports readiness only; message decoding and dispatch remain
 * the responsibility of the kernel coordinator.</p>
 */
public final class JupyterZmqPoller implements AutoCloseable {
    private final ZMQ.Poller delegate;
    private final Map<JupyterZmqChannel, Integer> indexes =
            new EnumMap<>(JupyterZmqChannel.class);
    private boolean closed;

    JupyterZmqPoller(ZContext context, Map<JupyterZmqChannel, ZMQ.Socket> sockets) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(sockets, "sockets must not be null");
        delegate = context.createPoller(sockets.size());
        try {
            for (JupyterZmqChannel channel : JupyterZmqChannel.values()) {
                ZMQ.Socket socket = Objects.requireNonNull(sockets.get(channel),
                        "Missing socket for channel " + channel);
                int index = delegate.register(socket, ZMQ.Poller.POLLIN);
                if (index < 0) {
                    throw new IllegalStateException("Unable to register " + channel + " with poller");
                }
                indexes.put(channel, index);
            }
        } catch (RuntimeException failure) {
            delegate.close();
            throw failure;
        }
    }

    /**
     * Polls for inbound messages.
     *
     * @param timeoutMillis zero returns immediately; a negative value is only valid for -1 (wait forever)
     * @return the channels readable during this poll cycle
     */
    public Set<JupyterZmqChannel> poll(long timeoutMillis) {
        ensureOpen();
        if (timeoutMillis < -1) {
            throw new IllegalArgumentException("timeoutMillis must be -1 or greater");
        }
        int readyCount = delegate.poll(timeoutMillis);
        if (readyCount < 0) {
            throw new IllegalStateException("ZeroMQ poll failed");
        }
        var ready = java.util.EnumSet.noneOf(JupyterZmqChannel.class);
        indexes.forEach((channel, index) -> {
            if (delegate.pollin(index)) ready.add(channel);
        });
        return Set.copyOf(ready);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            delegate.close();
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Jupyter ZeroMQ poller is closed");
    }
}
