package tech.kayys.javaland.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JavaExecutionSessionTest {
    private static final String[] COMPILER_OPTIONS = {
            "--source", Integer.toString(Runtime.version().feature())
    };

    @Test
    void evaluatesExpressionsAndRetainsDeclarationsAcrossCells() throws Exception {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            assertNull(session.eval("int answer = 40;"));
            assertEquals("42", session.eval("answer + 2"));
        }
    }

    @Test
    void evaluatesMultipleSnippetsInOneSubmission() throws Exception {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            assertEquals("42", session.eval("int base = 40;\nbase + 2"));
        }
    }

    @Test
    void classifiesCompleteAndIncompleteSource() {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            assertEquals("complete", session.isComplete("1 + 1"));
            assertEquals("incomplete", session.isComplete("if (true) {"));
        }
    }

    @Test
    void providesStateAwareCompletionSuggestionsAndReplacementAnchor() throws Exception {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            session.eval("void greetJavaLand() {}");
            var result = session.complete("greetJ", 6);
            assertEquals(0, result.cursorStart());
            assertTrue(result.matches().contains("greetJavaLand"));
        }
    }

    @Test
    void validatesCompletionCursorOffsets() {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            assertThrows(IllegalArgumentException.class, () -> session.complete("hello", 6));
            assertThrows(IllegalArgumentException.class, () -> session.complete("hello", -1));
        }
    }

    @Test
    void exposesJshellForCompletionAndClasspathIntegration() {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            assertNotNull(session.shell());
            assertFalse(session.isClosed());
        }
    }

    @Test
    void reportsCompilationDiagnostics() {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class, () -> session.eval("int broken = ;"));
            assertFalse(failure.getMessage().isBlank());
        }
    }

    @Test
    void surfacesExceptionsThrownByUserCode() {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> session.eval("throw new IllegalStateException(\"boom\");"));
            assertEquals("Java snippet execution failed", failure.getMessage());
            assertEquals("boom", failure.getCause().getMessage());
        }
    }

    @Test
    void supportsCustomStartupScriptAndCompilerOptions() throws Exception {
        try (JavaExecutionSession session = JavaExecutionSession.create(
                "int startupValue = 7;", COMPILER_OPTIONS)) {
            assertEquals("42", session.eval("startupValue * 6"));
        }
    }

    @Test
    void interruptStopsRunningEvaluationAndLeavesSessionUsable() throws Exception {
        String marker = "javaland.interrupt.test.started";
        System.clearProperty(marker);
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                    new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
            Thread evaluator = new Thread(() -> {
                try {
                    session.eval("System.setProperty(\"" + marker
                            + "\", \"true\"); while (true) { Thread.yield(); }");
                } catch (Throwable thrown) {
                    failure.set(thrown);
                } finally {
                    finished.countDown();
                }
            }, "javaland-jshell-interrupt-test");
            evaluator.setDaemon(true);
            evaluator.start();

            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!"true".equals(System.getProperty(marker))
                    && System.nanoTime() < deadline && evaluator.isAlive()) {
                Thread.sleep(10);
            }
            assertEquals("true", System.getProperty(marker),
                    "the JShell evaluation should enter the long-running snippet");

            session.interrupt();

            assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "interrupt should stop the active JShell evaluation");
            assertNull(failure.get(), "stopping an evaluation should not corrupt the session");
            assertEquals("42", session.eval("6 * 7"),
                    "the session should accept a new evaluation after interruption");
        } finally {
            System.clearProperty(marker);
        }
    }

    @Test
    void routesSnippetOutputToCallerOwnedStreams() throws Exception {
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();
        try (JavaExecutionSession session = JavaExecutionSession.create(stdout, stderr)) {
            session.eval("System.out.println(\"captured stdout\"); System.err.println(\"captured stderr\");");
        }
        assertTrue(stdout.toString(java.nio.charset.StandardCharsets.UTF_8).contains("captured stdout"));
        assertTrue(stderr.toString(java.nio.charset.StandardCharsets.UTF_8).contains("captured stderr"));
    }

    @Test
    void closeIsIdempotentAndRejectsFurtherUse() {
        JavaExecutionSession session = JavaExecutionSession.create();
        session.close();
        session.close();
        assertTrue(session.isClosed());
        assertThrows(IllegalStateException.class, () -> session.eval("1 + 1"));
        assertThrows(IllegalStateException.class, session::shell);
        assertThrows(IllegalStateException.class, () -> session.complete("x", 1));
    }
}
