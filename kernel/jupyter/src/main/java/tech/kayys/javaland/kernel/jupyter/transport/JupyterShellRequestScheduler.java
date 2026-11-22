package tech.kayys.javaland.kernel.jupyter.transport;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serializes Shell work while bounding the number of requests accepted ahead of execution.
 *
 * <p>When the pending slot is full, callers must leave the Shell socket unread. This applies
 * backpressure without blocking the coordinator thread, which must remain available for
 * Control-channel interrupts and heartbeat traffic.</p>
 */
final class JupyterShellRequestScheduler implements AutoCloseable {
    static final int DEFAULT_PENDING_CAPACITY = 1;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final ThreadPoolExecutor executor;

    JupyterShellRequestScheduler() {
        this(DEFAULT_PENDING_CAPACITY);
    }

    JupyterShellRequestScheduler(int pendingCapacity) {
        if (pendingCapacity < 1) {
            throw new IllegalArgumentException("pendingCapacity must be positive");
        }
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "javaland-jupyter-shell");
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(pendingCapacity), threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Whether another request can be accepted without blocking the coordinator. */
    boolean hasCapacity() {
        return !closed.get() && !executor.isShutdown()
                && executor.getQueue().remainingCapacity() > 0;
    }

    /** Submit one request, returning false when closed or saturated. */
    boolean submit(Runnable request) {
        if (closed.get()) {
            return false;
        }
        try {
            executor.execute(request);
            return true;
        } catch (RejectedExecutionException rejected) {
            return false;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
