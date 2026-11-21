package tech.kayys.javaland.runtime;

import org.dflib.jjava.execution.CodeEvaluator;
import org.dflib.jjava.execution.CodeEvaluatorBuilder;
import jdk.jshell.JShell;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one persistent Java execution session, independently of any notebook protocol. */
public final class JavaExecutionSession implements AutoCloseable {
    private static final String DEFAULT_STARTUP_SCRIPT = String.join("\n",
            "import java.util.*;", "import java.nio.file.*;", "import java.time.*;", "import java.math.*;");
    private final CodeEvaluator evaluator;
    private final AtomicBoolean closed = new AtomicBoolean();

    private JavaExecutionSession(String startupScript, String... compilerOptions) {
        this.evaluator = new CodeEvaluatorBuilder().sysStdout().sysStderr().sysStdin()
                .compilerOpts(compilerOptions).startupScript(startupScript).build();
    }

    /** Creates a session with JavaLand's standard Java imports and compiler defaults. */
    public static JavaExecutionSession create() {
        return create(DEFAULT_STARTUP_SCRIPT, "--enable-preview", "--source",
                Integer.toString(Runtime.version().feature()), "--add-modules=jdk.incubator.vector");
    }

    /** Creates a session with explicit startup source and JShell compiler options. */
    public static JavaExecutionSession create(String startupScript, String... compilerOptions) {
        if (startupScript == null) throw new IllegalArgumentException("startupScript must not be null");
        if (compilerOptions == null) throw new IllegalArgumentException("compilerOptions must not be null");
        return new JavaExecutionSession(startupScript, compilerOptions.clone());
    }

    /** Evaluates source in the persistent session. */
    public Object eval(String source) throws Exception {
        ensureOpen();
        return evaluator.eval(source);
    }

    /** Returns JShell's source completeness classification. */
    public String isComplete(String source) {
        ensureOpen();
        return evaluator.isComplete(source);
    }

    /** Interrupts a currently running evaluation, if any. */
    public void interrupt() {
        if (!closed.get()) evaluator.interrupt();
    }

    /** Exposes the JDK JShell instance for editor integrations such as completion. */
    public JShell shell() {
        ensureOpen();
        return evaluator.getShell();
    }

    /** Adds a JAR or directory to the active JShell class path. */
    public void addToClasspath(String path) {
        ensureOpen();
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path must not be blank");
        evaluator.getShell().addToClasspath(path);
    }

    public boolean isClosed() { return closed.get(); }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            evaluator.shutdown();
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Java execution session is closed");
    }
}
