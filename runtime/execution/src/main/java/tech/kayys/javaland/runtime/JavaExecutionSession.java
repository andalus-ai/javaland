package tech.kayys.javaland.runtime;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns one persistent Java execution session, independently of any notebook protocol.
 *
 * <p>All session operations are serialized because {@link JShell} is not thread-safe.
 * {@link #interrupt()} is intentionally lock-free so it can stop an evaluation running
 * on another thread.</p>
 */
public final class JavaExecutionSession implements AutoCloseable {
    private static final String DEFAULT_STARTUP_SCRIPT = String.join("\n",
            "import java.util.*;",
            "import java.nio.file.*;",
            "import java.time.*;",
            "import java.math.*;");

    private final JShell shell;
    private final AtomicBoolean closed = new AtomicBoolean();

    private JavaExecutionSession(String startupScript, String... compilerOptions) {
        this.shell = JShell.builder()
                .in(System.in)
                .out(System.out)
                .err(System.err)
                .compilerOptions(compilerOptions)
                .build();
        try {
            evaluateSource(startupScript);
        } catch (RuntimeException failure) {
            shell.close();
            throw failure;
        }
    }

    /** Creates a session with JavaLand's standard Java imports and compiler defaults. */
    public static JavaExecutionSession create() {
        return create(DEFAULT_STARTUP_SCRIPT,
                "--enable-preview",
                "--source", Integer.toString(Runtime.version().feature()),
                "--add-modules=jdk.incubator.vector");
    }

    /** Creates a session with explicit startup source and JShell compiler options. */
    public static JavaExecutionSession create(String startupScript, String... compilerOptions) {
        Objects.requireNonNull(startupScript, "startupScript must not be null");
        Objects.requireNonNull(compilerOptions, "compilerOptions must not be null");
        return new JavaExecutionSession(startupScript, compilerOptions.clone());
    }

    /** Evaluates one or more complete snippets in the persistent session. */
    public synchronized Object eval(String source) throws Exception {
        ensureOpen();
        Objects.requireNonNull(source, "source must not be null");
        return evaluateSource(source);
    }

    /** Returns the Jupyter completeness token for the supplied source. */
    public synchronized String isComplete(String source) {
        ensureOpen();
        Objects.requireNonNull(source, "source must not be null");
        return isSourceComplete(source) ? "complete" : "incomplete";
    }

    /**
     * Computes context-aware completions against the current persistent JShell state.
     *
     * @param source source text up to the editor cursor
     * @param cursor cursor offset in Java UTF-16 code units
     * @return replacement start offset and candidate continuations
     */
    public synchronized CompletionResult complete(String source, int cursor) {
        ensureOpen();
        Objects.requireNonNull(source, "source must not be null");
        if (cursor < 0 || cursor > source.length()) {
            throw new IllegalArgumentException("cursor must be between 0 and source length");
        }
        int[] anchor = new int[1];
        List<String> matches = shell.sourceCodeAnalysis()
                .completionSuggestions(source, cursor, anchor)
                .stream()
                .map(SourceCodeAnalysis.Suggestion::continuation)
                .distinct()
                .toList();
        return new CompletionResult(anchor[0], matches);
    }

    /** Result of a context-aware source completion query. */
    public record CompletionResult(int cursorStart, List<String> matches) {
        public CompletionResult {
            if (cursorStart < 0) {
                throw new IllegalArgumentException("cursorStart must not be negative");
            }
            matches = List.copyOf(Objects.requireNonNull(matches, "matches must not be null"));
        }
    }

    /** Attempts to stop user code currently executing in this session. */
    public void interrupt() {
        if (!closed.get()) {
            shell.stop();
        }
    }

    /**
     * Exposes the JDK JShell instance for advanced integrations.
     * Callers must not access it concurrently with session operations.
     */
    public synchronized JShell shell() {
        ensureOpen();
        return shell;
    }

    /** Adds a JAR or directory to the active JShell class path. */
    public synchronized void addToClasspath(String path) {
        ensureOpen();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        shell.addToClasspath(path);
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            shell.stop();
            synchronized (this) {
                shell.close();
            }
        }
    }

    private Object evaluateSource(String source) {
        String remaining = source;
        Object lastValue = null;

        while (!remaining.isBlank()) {
            var completion = shell.sourceCodeAnalysis().analyzeCompletion(remaining);
            if (!completion.completeness().isComplete()) {
                throw new IllegalArgumentException("Incomplete Java source");
            }

            String snippetSource = completion.source();
            if (snippetSource.isBlank()) {
                break;
            }

            List<SnippetEvent> events = shell.eval(snippetSource);
            for (SnippetEvent event : events) {
                if (event.status() == Snippet.Status.REJECTED) {
                    String diagnostics = shell.diagnostics(event.snippet())
                            .map(diagnostic -> diagnostic.getMessage(java.util.Locale.ROOT))
                            .reduce((left, right) -> left + System.lineSeparator() + right)
                            .orElse("Java snippet was rejected");
                    throw new IllegalArgumentException(diagnostics);
                }
                if (event.exception() != null) {
                    throw new IllegalStateException("Java snippet execution failed", event.exception());
                }
                if (event.snippet().kind() == Snippet.Kind.EXPRESSION && event.value() != null) {
                    lastValue = event.value();
                }
            }
            remaining = completion.remaining();
        }
        return lastValue;
    }

    private boolean isSourceComplete(String source) {
        String remaining = source;
        while (!remaining.isBlank()) {
            var completion = shell.sourceCodeAnalysis().analyzeCompletion(remaining);
            if (!completion.completeness().isComplete()) {
                return false;
            }
            if (completion.source().isBlank()) {
                return true;
            }
            remaining = completion.remaining();
        }
        return true;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Java execution session is closed");
        }
    }
}
