package tech.kayys.javaland.kernel.jupyter.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Blocking notebook input stream. A read requests input from the frontend; an
 * input_reply is supplied with {@link #submit(String)} by the coordinator thread.
 */
public final class JupyterStdinInputStream extends InputStream {
    @FunctionalInterface
    public interface InputRequester {
        void request(String prompt, boolean password);
    }

    private final InputRequester requester;
    private final BlockingQueue<byte[]> replies = new ArrayBlockingQueue<>(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean awaitingReply = new AtomicBoolean();
    private byte[] current = new byte[0];
    private int offset;

    public JupyterStdinInputStream(InputRequester requester) {
        this.requester = Objects.requireNonNull(requester, "requester must not be null");
    }

    /** Supplies one frontend input reply. A newline is appended for normal line-oriented Java APIs. */
    public void submit(String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (closed.get()) {
            throw new IllegalStateException("Notebook stdin is closed");
        }
        if (!awaitingReply.compareAndSet(true, false)) {
            throw new IllegalStateException("No notebook input request is pending");
        }
        byte[] bytes = (value + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (!replies.offer(bytes)) {
            throw new IllegalStateException("No notebook input request is pending");
        }
    }

    @Override
    public synchronized int read() throws IOException {
        if (!ensureInput()) {
            return -1;
        }
        return current[offset++] & 0xff;
    }

    @Override
    public synchronized int read(byte[] bytes, int start, int length) throws IOException {
        Objects.checkFromIndexSize(start, length, bytes.length);
        if (length == 0) {
            return 0;
        }
        if (!ensureInput()) {
            return -1;
        }
        int count = Math.min(length, current.length - offset);
        System.arraycopy(current, offset, bytes, start, count);
        offset += count;
        return count;
    }

    private boolean ensureInput() throws IOException {
        while (offset >= current.length) {
            if (closed.get()) {
                return false;
            }
            awaitingReply.set(true);
            try {
                requester.request("", false);
                current = replies.take();
                offset = 0;
            } catch (InterruptedException interrupted) {
                awaitingReply.set(false);
                Thread.currentThread().interrupt();
                InterruptedIOException failure = new InterruptedIOException(
                        "Interrupted while waiting for notebook input");
                failure.initCause(interrupted);
                throw failure;
            } catch (RuntimeException failure) {
                awaitingReply.set(false);
                throw failure;
            }
        }
        return true;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            replies.offer(new byte[0]);
        }
    }
}
