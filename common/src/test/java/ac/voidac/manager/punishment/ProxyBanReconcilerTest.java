package ac.voidac.manager.punishment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyBanReconcilerTest {

    private static ActiveBanRecord record(UUID uuid, String banId) {
        return new ActiveBanRecord(banId, uuid, "player", "reason", 0L, 1L);
    }

    @Test
    void banLiftedByTheProxyIsDropped() {
        UUID uuid = UUID.randomUUID();

        List<UUID> stale = ProxyBanReconciler.staleBans(
                List.of(record(uuid, "VOID-000001")), Map.of(uuid, "VOID-000001"), Map.of());

        assertEquals(List.of(uuid), stale);
    }

    @Test
    void banStillHeldByTheProxyIsKept() {
        UUID uuid = UUID.randomUUID();

        List<UUID> stale = ProxyBanReconciler.staleBans(
                List.of(record(uuid, "VOID-000001")),
                Map.of(uuid, "VOID-000001"),
                Map.of(uuid, "VOID-000001"));

        assertTrue(stale.isEmpty());
    }

    @Test
    void banTheProxyNeverAcknowledgedIsKept() {
        UUID uuid = UUID.randomUUID();

        List<UUID> stale = ProxyBanReconciler.staleBans(
                List.of(record(uuid, "VOID-000001")), Map.of(), Map.of());

        assertTrue(stale.isEmpty());
    }

    @Test
    void localBanReplacingAProxyBanIsKept() {
        UUID uuid = UUID.randomUUID();

        List<UUID> stale = ProxyBanReconciler.staleBans(
                List.of(record(uuid, "VOID-000007")), Map.of(uuid, "VOID-000001"), Map.of());

        assertTrue(stale.isEmpty());
    }

    @Test
    void banIdCollisionWithAnotherPlayerDoesNotKeepAStaleBan() {
        UUID lifted = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        List<UUID> stale = ProxyBanReconciler.staleBans(
                List.of(record(lifted, "VOID-000042")),
                Map.of(lifted, "VOID-000042"),
                Map.of(other, "VOID-000042"));

        assertEquals(List.of(lifted), stale);
    }
}
