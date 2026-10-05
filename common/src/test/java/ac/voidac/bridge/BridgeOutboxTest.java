package ac.voidac.bridge;

import ac.voidac.bridge.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeOutboxTest {

    private final List<String> delivered = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean up = true;
    private volatile CountDownLatch gate;
    private volatile boolean explode;
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile CountDownLatch entered = new CountDownLatch(1);
    private BridgeOutbox outbox;

    private BridgeOutbox create(int maxPending, int maxQueuedWrites) {
        outbox = new BridgeOutbox((type, payload) -> {
            attempts.incrementAndGet();
            entered.countDown();
            CountDownLatch latch = gate;
            if (latch != null) {
                try {
                    latch.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (explode) throw new IllegalStateException("boom");
            if (!up) return false;
            delivered.add(type + ":" + new String(payload));
            return true;
        }, maxPending, maxQueuedWrites, "test-outbox");
        return outbox;
    }

    @AfterEach
    void tearDown() {
        if (gate != null) gate.countDown();
        if (outbox != null) outbox.shutdown(500);
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("condition not met in time");
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes();
    }

    @Test
    void framesAreDeliveredInSubmissionOrder() {
        create(10, 10);
        outbox.submit(MessageType.BAN, bytes("1"));
        outbox.submit(MessageType.ALERT, bytes("2"));
        outbox.submit(MessageType.UNBAN, bytes("3"));

        await(() -> delivered.size() == 3);
        assertEquals(List.of("BAN:1", "ALERT:2", "UNBAN:3"), delivered);
    }

    @Test
    void submitNeverBlocksWhileTheTransportIsStuck() {
        create(5, 10);
        gate = new CountDownLatch(1);

        long start = System.nanoTime();
        for (int i = 0; i < 2_000; i++) {
            outbox.submit(i % 2 == 0 ? MessageType.ALERT : MessageType.BAN, bytes(String.valueOf(i)));
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMs < 1_000, "submit blocked for " + elapsedMs + " ms");
        assertTrue(outbox.pendingCount() <= 5);
    }

    @Test
    void overflowingBansKeepTheNewestOnes() throws InterruptedException {
        create(3, 1);
        gate = new CountDownLatch(1);
        outbox.submit(MessageType.BAN, bytes("held"));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        outbox.submit(MessageType.BAN, bytes("queued"));
        for (int i = 1; i <= 5; i++) {
            outbox.submit(MessageType.BAN, bytes("overflow" + i));
        }

        assertEquals(3, outbox.pendingCount());
        gate.countDown();
        await(() -> delivered.size() == 5);

        assertTrue(delivered.containsAll(List.of("BAN:held", "BAN:queued", "BAN:overflow3", "BAN:overflow4", "BAN:overflow5")));
        assertEquals(0, outbox.pendingCount());
    }

    @Test
    void bansFailedWhileDisconnectedAreSentFirstOnceBackUp() {
        create(10, 10);
        up = false;
        outbox.submit(MessageType.BAN, bytes("1"));
        outbox.submit(MessageType.UNBAN, bytes("2"));
        await(() -> outbox.pendingCount() == 2);

        up = true;
        outbox.submit(MessageType.BAN, bytes("3"));

        await(() -> delivered.size() == 3);
        assertEquals(List.of("BAN:1", "UNBAN:2", "BAN:3"), delivered);
        assertEquals(0, outbox.pendingCount());
    }

    @Test
    void flushDeliversWhatWasParked() {
        create(10, 10);
        up = false;
        outbox.submit(MessageType.BAN, bytes("1"));
        await(() -> outbox.pendingCount() == 1);

        up = true;
        outbox.flush();

        await(() -> delivered.size() == 1);
        assertEquals(List.of("BAN:1"), delivered);
    }

    @Test
    void alertsAreNotKeptWhenTheyCannotBeSent() {
        create(10, 10);
        up = false;
        outbox.submit(MessageType.ALERT, bytes("a"));
        outbox.submit(MessageType.ALERT, bytes("b"));
        await(() -> attempts.get() == 2);

        up = true;
        outbox.submit(MessageType.ALERT, bytes("c"));
        await(() -> !delivered.isEmpty());

        assertEquals(List.of("ALERT:c"), delivered);
        assertEquals(0, outbox.pendingCount());
    }

    @Test
    void aThrowingTransportParksTheBanAndKeepsTheWriterAlive() {
        create(10, 10);
        explode = true;
        outbox.submit(MessageType.BAN, bytes("1"));
        await(() -> outbox.pendingCount() == 1);

        explode = false;
        outbox.flush();

        await(() -> delivered.size() == 1);
        assertEquals(List.of("BAN:1"), delivered);
    }

    @Test
    void clearDropsEverythingParked() {
        create(10, 10);
        up = false;
        outbox.submit(MessageType.BAN, bytes("1"));
        await(() -> outbox.pendingCount() == 1);

        outbox.clear();

        assertEquals(0, outbox.pendingCount());
    }

    @Test
    void shutdownDeliversWhatIsAlreadyQueued() {
        create(10, 10);
        for (int i = 0; i < 5; i++) {
            outbox.submit(MessageType.BAN, bytes(String.valueOf(i)));
        }

        outbox.shutdown(2_000);

        assertEquals(5, delivered.size());
    }

    @Test
    void submitAfterShutdownKeepsTheBanForLater() {
        create(10, 10);
        outbox.shutdown(500);

        outbox.submit(MessageType.BAN, bytes("late"));

        assertEquals(1, outbox.pendingCount());
        assertTrue(delivered.isEmpty());
    }
}
