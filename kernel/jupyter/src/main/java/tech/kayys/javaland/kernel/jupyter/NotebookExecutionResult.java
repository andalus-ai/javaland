package tech.kayys.javaland.kernel.jupyter;

import org.dflib.jjava.jupyter.kernel.display.DisplayData;

/** Result of one notebook cell, including output captured from its JShell evaluation. */
public record NotebookExecutionResult(
        DisplayData displayData,
        String stdout,
        String stderr,
        Exception failure) {

    public NotebookExecutionResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }

    public boolean successful() {
        return failure == null;
    }
}
