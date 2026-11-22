package tech.kayys.javaland.kernel.jupyter.transport;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serializes Shell work while bounding accepted requests until their responses are sent.
 *
 * <p>Capacity includes both queued/running work and completed responses awaiting the
 * coordinator. This keeps a fast worker from building an unbounded response backlog.</p>
 */
final class JupyterShellRequestScheduler implements AutoCloseable {
    static final int DEFAULT_PENDING_CAPACITY = 1;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Semaphore outstanding;
    private final ThreadPoolExecutor executor;

    JupyterShellRequestScheduler() {
        this(DEFAULT_PENDING_CAPACITY);
    }

    JupyterShellRequestScheduler(int pendingCapacity) {
        if (pendingCapacity < 1) {
            throw new IllegalArgumentException("pendingCapacity must be positive");
        }
        outstanding = new Semaphore(pendingCapacity + 1);
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
        return !closed.get() && outstanding.availablePermits() > 0;
    }

    /** Submit one request, returning false when closed or saturated. */
    boolean submit(Runnable request) {
        if (closed.get() || !outstanding.tryAcquire()) {
            return false;
        }
        try {
            executor.execute(request);
            return true;
        } catch (RejectedExecutionException rejected) {
            outstanding.release();
            return false;
        }
    }

    /** Release capacity after the corresponding response has been handled by the coordinator. */
    void complete() {
        if (!closed.get()) {
            outstanding.release();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
