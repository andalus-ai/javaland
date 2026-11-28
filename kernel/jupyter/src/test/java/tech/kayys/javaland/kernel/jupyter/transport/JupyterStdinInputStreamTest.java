package tech.kayys.javaland.kernel.jupyter.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JupyterStdinInputStreamTest {
    @Test
    void requestsAndReadsLineOrientedInput() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch requested = new CountDownLatch(1);
        try (JupyterStdinInputStream input = new JupyterStdinInputStream((prompt, password) -> {
            assertEquals("", prompt);
            assertFalse(password);
            requests.incrementAndGet();
            requested.countDown();
        })) {
            Thread submitter = new Thread(() -> {
                try {
                    assertTrue(requested.await(2, TimeUnit.SECONDS));
                    input.submit("hello");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            });
            submitter.start();
            assertEquals("hello", new String(input.readNBytes(6), StandardCharsets.UTF_8).trim());
            submitter.join(2_000);
            assertEquals(1, requests.get());
        }
    }

    @Test
    void blocksUntilFrontendSuppliesInput() throws Exception {
        CountDownLatch requested = new CountDownLatch(1);
        try (JupyterStdinInputStream input = new JupyterStdinInputStream((prompt, password) ->
                requested.countDown())) {
            Thread reader = new Thread(() -> {
                try {
                    assertEquals('x', input.read());
                } catch (IOException failure) {
                    throw new AssertionError(failure);
                }
            });
            reader.start();
            assertTrue(requested.await(2, TimeUnit.SECONDS));
            input.submit("x");
            reader.join(2_000);
            assertFalse(reader.isAlive());
        }
    }

    @Test
    void closeUnblocksWaitingReader() throws Exception {
        CountDownLatch requested = new CountDownLatch(1);
        JupyterStdinInputStream input = new JupyterStdinInputStream((prompt, password) ->
                requested.countDown());
        AtomicInteger result = new AtomicInteger(Integer.MIN_VALUE);
        Thread reader = new Thread(() -> {
            try {
                result.set(input.read());
            } catch (IOException failure) {
                throw new AssertionError(failure);
            }
        });
        reader.start();
        assertTrue(requested.await(2, TimeUnit.SECONDS));
        input.close();
        reader.join(2_000);
        assertFalse(reader.isAlive());
        assertEquals(-1, result.get());
    }

    @Test
    void cancellationUnblocksReadAndAllowsAnotherPrompt() throws Exception {
        CountDownLatch firstRequest = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        try (JupyterStdinInputStream input = new JupyterStdinInputStream((prompt, password) -> {
            if (requests.incrementAndGet() == 1) {
                firstRequest.countDown();
            }
        })) {
            AtomicInteger outcome = new AtomicInteger(Integer.MIN_VALUE);
            Thread reader = new Thread(() -> {
                try {
                    outcome.set(input.read());
                } catch (IOException failure) {
                    outcome.set(-2);
                }
            });
            reader.start();
            assertTrue(firstRequest.await(2, TimeUnit.SECONDS));
            input.cancelPending();
            reader.join(2_000);
            assertFalse(reader.isAlive());
            assertEquals(-2, outcome.get());

            Thread nextReader = new Thread(() -> {
                try {
                    outcome.set(input.read());
                } catch (IOException failure) {
                    outcome.set(-3);
                }
            });
            nextReader.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (requests.get() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            input.submit("r");
            nextReader.join(2_000);
            assertEquals('r', outcome.get());
        }
    }

    @Test
    void rejectsSecondReplyWhenOneIsAlreadyQueued() {
        try (JupyterStdinInputStream input = new JupyterStdinInputStream((prompt, password) -> {})) {
            assertThrows(IllegalStateException.class, () -> input.submit("first"));
        }
    }
}
