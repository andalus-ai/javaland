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
            session.eval("int answer = 40;");
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
                    IllegalArgumentException.class,
                    () -> session.eval("int broken = ;"));
            assertFalse(failure.getMessage().isBlank());
        }
    }

    @Test
    void surfacesExceptionsThrownByUserCode() {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
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
    void closeIsIdempotentAndRejectsFurtherUse() {
        JavaExecutionSession session = JavaExecutionSession.create();
        session.close();
        session.close();
        assertTrue(session.isClosed());
        assertThrows(IllegalStateException.class, () -> session.eval("1 + 1"));
        assertThrows(IllegalStateException.class, session::shell);
    }
}
