package ac.voidac.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanWaveManagerTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void firstWaveWaitsFromTheOldestQueuedEntry() {
        assertFalse(BanWaveManager.isAutoExecutionDue(
                NOW, 0, NOW - TimeUnit.DAYS.toMillis(1), 7));
        assertTrue(BanWaveManager.isAutoExecutionDue(
                NOW, 0, NOW - TimeUnit.DAYS.toMillis(8), 7));
    }

    @Test
    void laterWavesWaitFromThePreviousExecution() {
        assertFalse(BanWaveManager.isAutoExecutionDue(
                NOW, NOW - TimeUnit.DAYS.toMillis(1), NOW - TimeUnit.DAYS.toMillis(20), 7));
        assertTrue(BanWaveManager.isAutoExecutionDue(
                NOW, NOW - TimeUnit.DAYS.toMillis(8), NOW - TimeUnit.DAYS.toMillis(1), 7));
    }

    @Test
    void automaticExecutionStaysDisabledForInvalidIntervals() {
        assertFalse(BanWaveManager.isAutoExecutionDue(NOW, 0, NOW - TimeUnit.DAYS.toMillis(8), 0));
        assertFalse(BanWaveManager.isAutoExecutionDue(NOW, 0, 0, 7));
    }

    @Test
    void queuePersistencePreservesCustomReasons() {
        BanWaveManager.BanEntry entry = new BanWaveManager.BanEntry(
                UUID.fromString("f84c6a79-30b7-45d4-8ab7-47f5da829c4e"),
                "PlayerOne", "manual", "Console", NOW, "7d", "Combat | movement exploit");

        assertEquals(entry, BanWaveManager.parseEntry(BanWaveManager.serializeEntry(entry)));
    }

    @Test
    void queuePersistenceReadsLegacyEntriesWithoutAReason() {
        BanWaveManager.BanEntry entry = BanWaveManager.parseEntry(
                "f84c6a79-30b7-45d4-8ab7-47f5da829c4e|PlayerOne|manual|Console|1800000000000|7d");

        assertEquals("7d", entry.duration());
        assertNull(entry.reason());
    }

    @Test
    void waveMessageUsesTheEntryReason() {
        BanWaveManager.BanEntry entry = new BanWaveManager.BanEntry(
                UUID.fromString("f84c6a79-30b7-45d4-8ab7-47f5da829c4e"),
                "PlayerOne", "manual", "Console", NOW, "7d", "Combat hacks");

        assertTrue(new BanWaveManager().formatKickReason(entry, "4").contains("Combat hacks"));
    }

    @Test
    void waveMessageFallsBackToCheating() {
        BanWaveManager.BanEntry entry = new BanWaveManager.BanEntry(
                UUID.fromString("f84c6a79-30b7-45d4-8ab7-47f5da829c4e"),
                "PlayerOne", "manual", "Console", NOW, "7d", null);

        assertTrue(new BanWaveManager().formatKickReason(entry, "4").contains("Cheating"));
    }
}
