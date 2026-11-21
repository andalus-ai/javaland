package tech.kayys.javaland.runtime;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class JavaExecutionSessionTest {
    @Test
    void evaluatesExpressionsAndRetainsDeclarationsAcrossCells() throws Exception {
        try (JavaExecutionSession session = JavaExecutionSession.create()) {
            session.eval("int answer = 40;");
            assertEquals("42", session.eval("answer + 2").toString());
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
    void closeIsIdempotentAndRejectsFurtherUse() {
        JavaExecutionSession session = JavaExecutionSession.create();
        session.close();
        session.close();
        assertTrue(session.isClosed());
        assertThrows(IllegalStateException.class, () -> session.eval("1 + 1"));
        assertThrows(IllegalStateException.class, session::shell);
    }

    @Test
    void supportsCustomStartupScriptAndCompilerOptions() throws Exception {
        try (JavaExecutionSession session = JavaExecutionSession.create(
                "int startupValue = 7;", "--source", Integer.toString(Runtime.version().feature()))) {
            assertEquals("42", session.eval("startupValue * 6").toString());
        }
    }
}
