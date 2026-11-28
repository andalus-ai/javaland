package tech.kayys.javaland.kernel.jupyter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Routes JShell output into the capture scope active on the evaluating thread.
 *
 * <p>It never replaces process-wide {@code System.out} or {@code System.err}.
 * Output written outside an active cell scope is forwarded to the original streams.</p>
 */
final class NotebookOutputCapture {
    private static final int MAX_CAPTURE_BYTES = 1024 * 1024;
    private final AtomicReference<Buffers> active = new AtomicReference<>();
    private final PrintStream stdout;
    private final PrintStream stderr;

    NotebookOutputCapture(PrintStream fallbackOut, PrintStream fallbackErr) {
        Objects.requireNonNull(fallbackOut, "fallbackOut must not be null");
        Objects.requireNonNull(fallbackErr, "fallbackErr must not be null");
        this.stdout = new PrintStream(new RoutedOutputStream(fallbackOut, true), true, StandardCharsets.UTF_8);
        this.stderr = new PrintStream(new RoutedOutputStream(fallbackErr, false), true, StandardCharsets.UTF_8);
    }

    PrintStream stdout() {
        return stdout;
    }

    PrintStream stderr() {
        return stderr;
    }

    Scope begin() {
        Buffers buffers = new Buffers();
        if (!active.compareAndSet(null, buffers)) {
            throw new IllegalStateException("Notebook output capture is already active");
        }
        return new Scope(buffers);
    }

    final class Scope implements AutoCloseable {
        private final Buffers buffers;
        private boolean closed;

        private Scope(Buffers buffers) {
            this.buffers = buffers;
        }

        String stdoutText() {
            return buffers.stdout.toString(StandardCharsets.UTF_8);
        }

        String stderrText() {
            return buffers.stderr.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            if (!closed) {
                active.remove();
                closed = true;
            }
        }
    }

    private final class RoutedOutputStream extends OutputStream {
        private final PrintStream fallback;
        private final boolean standardOutput;

        private RoutedOutputStream(PrintStream fallback, boolean standardOutput) {
            this.fallback = fallback;
            this.standardOutput = standardOutput;
        }

        @Override
        public void write(int value) throws IOException {
            Buffers buffers = active.get();
            if (buffers == null) {
                fallback.write(value);
            } else {
                target(buffers).write(value);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Buffers buffers = active.get();
            if (buffers == null) {
                fallback.write(bytes, offset, length);
            } else {
                target(buffers).write(bytes, offset, length);
            }
        }

        @Override
        public void flush() {
            Buffers buffers = active.get();
            if (buffers == null) {
                fallback.flush();
            } else {
                fallback.flush();
            }
        }

        private ByteArrayOutputStream target(Buffers buffers) {
            return standardOutput ? buffers.stdout : buffers.stderr;
        }
    }

    private static final class Buffers {
        private final BoundedBuffer stdout = new BoundedBuffer();
        private final BoundedBuffer stderr = new BoundedBuffer();
    }

    private static final class BoundedBuffer extends ByteArrayOutputStream {
        private boolean truncated;

        @Override
        public synchronized void write(int value) {
            if (count < MAX_CAPTURE_BYTES) {
                super.write(value);
            } else {
                truncated = true;
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            int accepted = Math.min(length, MAX_CAPTURE_BYTES - count);
            if (accepted > 0) {
                super.write(bytes, offset, accepted);
            }
            if (accepted < length) {
                truncated = true;
            }
        }

        @Override
        public synchronized String toString(java.nio.charset.Charset charset) {
            String text = super.toString(charset);
            return truncated ? text + System.lineSeparator()
                    + "[JavaLand: cell output truncated after " + MAX_CAPTURE_BYTES + " bytes]"
                    : text;
        }
    }
}
