package tech.kayys.javaland.kernel.jupyter;

/** Framework-neutral declarations loaded into a fresh JShell session. */
final class NotebookStartupScripts {
    private static final String DEFAULT_STARTUP_SCRIPT = String.join("\n",
            "import java.util.*;",
            "import java.nio.file.*;",
            "import java.time.*;",
            "import java.math.*;"
    );

    private NotebookStartupScripts() { }

    static String defaultScript() {
        return DEFAULT_STARTUP_SCRIPT;
    }
}
