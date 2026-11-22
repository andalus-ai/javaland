package tech.kayys.javaland.kernel.jupyter.transport;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class JupyterShellRequestSchedulerTest {
    @Test
    void boundsPendingRequestsWithoutBlockingTheCoordinator() throws Exception {
        try (var scheduler = new JupyterShellRequestScheduler(1)) {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var secondFinished = new CountDownLatch(1);

            assertTrue(scheduler.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            assertTrue(scheduler.submit(secondFinished::countDown));
            assertFalse(scheduler.hasCapacity());
            assertFalse(scheduler.submit(() -> fail("a saturated scheduler must reject work")));

            release.countDown();
            assertTrue(secondFinished.await(5, TimeUnit.SECONDS));
            assertTrue(scheduler.hasCapacity());
        }
    }

    @Test
    void closeCancelsPendingWorkAndRejectsFurtherSubmissions() throws Exception {
        var scheduler = new JupyterShellRequestScheduler(1);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pendingRan = new AtomicBoolean();

        assertTrue(scheduler.submit(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(scheduler.submit(() -> pendingRan.set(true)));

        scheduler.close();
        release.countDown();

        assertFalse(scheduler.hasCapacity());
        assertFalse(scheduler.submit(() -> fail("a closed scheduler must reject work")));
        assertFalse(pendingRan.get(), "queued requests must not start after close");
        scheduler.close();
    }

    @Test
    void rejectsInvalidPendingCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new JupyterShellRequestScheduler(0));
    }
}
