package tech.kayys.javaland.kernel.jupyter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    private final ThreadLocal<Buffers> active = new ThreadLocal<>();
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
        if (active.get() != null) {
            throw new IllegalStateException("Notebook output capture is already active on this thread");
        }
        Buffers buffers = new Buffers();
        active.set(buffers);
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
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    }
}
