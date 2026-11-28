package tech.kayys.javaland.kernel.jupyter;

import org.dflib.jjava.jupyter.kernel.display.DisplayData;
import java.util.List;

/** Result of one notebook cell, including captured streams and explicit display events. */
public record NotebookExecutionResult(
        DisplayData displayData,
        String stdout,
        String stderr,
        Exception failure,
        List<NotebookDisplayEvent> displayEvents) {

    public NotebookExecutionResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        displayEvents = displayEvents == null ? List.of() : List.copyOf(displayEvents);
    }

    public boolean successful() {
        return failure == null;
    }
}
