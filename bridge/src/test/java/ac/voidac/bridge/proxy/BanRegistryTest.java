package ac.voidac.bridge.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanRegistryTest {

    @TempDir
    Path directory;

    private static BanRegistry.Entry entry(String banId, UUID uuid, String name) {
        return new BanRegistry.Entry(banId, uuid, name, "cheating", 0L, 1L, "survival", Set.of());
    }

    @Test
    void bansFromDifferentBackendsSharingABanIdAreAllPersisted() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        BanRegistry registry = new BanRegistry(directory, platform);
        registry.put(entry("VOID-000042", first, "Alex"));
        registry.put(entry("VOID-000042", second, "Steve"));

        BanRegistry reloaded = new BanRegistry(directory, platform);
        reloaded.load();

        assertNotNull(reloaded.find(first, "Alex", null));
        assertNotNull(reloaded.find(second, "Steve", null));
    }

    @Test
    void banShadowedByNameIsStillPersisted() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        BanRegistry registry = new BanRegistry(directory, platform);
        registry.put(entry("VOID-000001", first, "Steve"));
        registry.put(entry("VOID-000002", second, "Steve"));

        BanRegistry reloaded = new BanRegistry(directory, platform);
        reloaded.load();

        assertNotNull(reloaded.find(first, null, null));
        assertNotNull(reloaded.find(second, null, null));
        assertNull(reloaded.find(UUID.randomUUID(), null, null));
    }

    private static BanRegistry.Entry scoped(String banId, UUID uuid, String name, long expiresAt, long issuedAt, String... servers) {
        return new BanRegistry.Entry(banId, uuid, name, "cheating", expiresAt, issuedAt, "survival", Set.of(servers));
    }

    @Test
    void bansOverDifferentScopesCoexist() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID uuid = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        registry.put(scoped("VOID-000001", uuid, "Alex", 0L, 1L, "survival"));
        registry.put(scoped("VOID-000002", uuid, "Alex", System.currentTimeMillis() + 3_600_000L, 2L, "creative"));

        assertEquals("VOID-000001", registry.find(uuid, "Alex", "survival").banId());
        assertEquals("VOID-000002", registry.find(uuid, "Alex", "creative").banId());
        assertNull(registry.find(uuid, "Alex", "lobby"));
    }

    @Test
    void banOverTheSameScopeReplacesTheOlderOne() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID uuid = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        registry.put(scoped("VOID-000001", uuid, "Alex", 0L, 1L, "survival"));
        assertTrue(registry.put(scoped("VOID-000002", uuid, "Alex", System.currentTimeMillis() + 3_600_000L, 2L, "survival")));

        assertEquals("VOID-000002", registry.find(uuid, "Alex", "survival").banId());
    }

    @Test
    void repeatingTheSameBanOverTheSameScopeChangesNothing() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID uuid = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        assertTrue(registry.put(scoped("VOID-000001", uuid, "Alex", 0L, 1L, "survival")));
        assertFalse(registry.put(scoped("VOID-000001", uuid, "Alex", 0L, 1L, "survival")));
    }

    @Test
    void anyBanQueryPrefersTheNetworkWideBan() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID uuid = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        registry.put(scoped("VOID-000001", uuid, "Alex", 0L, 1L, "survival"));
        registry.put(scoped("VOID-000002", uuid, "Alex", System.currentTimeMillis() + 3_600_000L, 2L));

        assertEquals("VOID-000002", registry.find(uuid, "Alex", null).banId());
    }

    @Test
    void syncCarriesTheStrongestBanPerPlayerForThatServer() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID uuid = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        registry.put(scoped("VOID-000001", uuid, "Alex", 0L, 1L, "survival"));
        registry.put(scoped("VOID-000002", uuid, "Alex", System.currentTimeMillis() + 3_600_000L, 2L));

        assertEquals(1, registry.activePayloads("survival").size());
        assertEquals("VOID-000001", registry.activePayloads("survival").get(0).banId());
        assertEquals("VOID-000002", registry.activePayloads("creative").get(0).banId());
    }

    @Test
    void removingByUuidLeavesAnotherPlayerWithTheSameNameAlone() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        registry.put(scoped("VOID-000001", first, "Steve", 0L, 1L));
        registry.put(scoped("VOID-000002", second, "Steve", 0L, 2L));

        assertTrue(registry.remove(first, "Steve"));

        assertNull(registry.find(first, null, null));
        assertNotNull(registry.find(second, null, null));
    }

    @Test
    void removingDropsEveryScopeOfThatPlayer() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID uuid = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        registry.put(scoped("VOID-000001", uuid, "Alex", 0L, 1L, "survival"));
        registry.put(scoped("VOID-000002", uuid, "Alex", 0L, 2L, "creative"));
        assertTrue(registry.remove(uuid, "Alex"));

        assertNull(registry.find(uuid, "Alex", "survival"));
        assertNull(registry.find(uuid, "Alex", "creative"));
        assertFalse(registry.remove(uuid, "Alex"));
    }

    @Test
    void removingByNameFindsBansStoredUnderAnotherUuid() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID stored = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        registry.put(scoped("VOID-000001", stored, "Alex", 0L, 1L));

        assertTrue(registry.remove(UUID.randomUUID(), "Alex"));
        assertNull(registry.find(stored, "Alex", null));
    }

    @Test
    void expiredBansAreDroppedAndNotPersisted() throws IOException {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID live = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);

        registry.put(scoped("VOID-000001", live, "Alex", 0L, 1L));
        registry.put(scoped("VOID-000002", expired, "Steve", System.currentTimeMillis() + 50L, 2L));
        sleep(120);

        assertNull(registry.find(expired, "Steve", null));
        assertNotNull(registry.find(live, "Alex", null));

        String saved = Files.readString(directory.resolve("bans.yml"));
        assertFalse(saved.contains("Steve"));
        assertTrue(saved.contains("Alex"));
    }

    @Test
    void banListWrittenByEarlierVersionsLoadsUnchanged() throws IOException {
        UUID first = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String legacy = """
                - ban-id: VOID-000010
                  uuid: 11111111-1111-1111-1111-111111111111
                  player-name: Alex
                  reason: Cheating
                  expires-at: 0
                  issued-at: 1700000000000
                  origin: survival
                  servers: []
                - ban-id: VOID-000011
                  uuid: 22222222-2222-2222-2222-222222222222
                  player-name: Steve
                  reason: Flying
                  expires-at: 0
                  issued-at: 1700000000001
                  origin: kitpvp
                  servers:
                  - lobby
                  - kitpvp
                - ban-id: VOID-000012
                  uuid: null
                  player-name: NameOnly
                  reason: Reach
                  expires-at: 0
                  issued-at: 1700000000002
                  origin: lobby
                  servers: []
                """;
        Files.writeString(directory.resolve("bans.yml"), legacy);

        BanRegistry registry = new BanRegistry(directory, new RecordingPlatform(directory));
        registry.load();

        assertEquals("VOID-000010", registry.find(first, "Alex", null).banId());
        assertEquals("VOID-000011", registry.find(second, "Steve", "kitpvp").banId());
        assertNull(registry.find(second, "Steve", "creative"));
        assertEquals("VOID-000012", registry.find(null, "nameonly", null).banId());
        assertEquals(2, registry.activePayloads(null).size());
    }

    @Test
    void existingBanFileIsBackedUpBeforeItIsRewritten() throws IOException {
        String legacy = """
                - ban-id: VOID-000010
                  uuid: 11111111-1111-1111-1111-111111111111
                  player-name: Alex
                  reason: Cheating
                  expires-at: 0
                  issued-at: 1700000000000
                  origin: survival
                  servers: []
                """;
        Files.writeString(directory.resolve("bans.yml"), legacy);

        BanRegistry registry = new BanRegistry(directory, new RecordingPlatform(directory));
        registry.load();
        registry.put(scoped("VOID-000020", UUID.randomUUID(), "Steve", 0L, 2L));

        assertEquals(legacy, Files.readString(directory.resolve("bans.yml.bak")));
        BanRegistry reloaded = new BanRegistry(directory, new RecordingPlatform(directory));
        reloaded.load();
        assertEquals(2, reloaded.activePayloads(null).size());
    }

    @Test
    void scopedBansSurviveARestart() {
        RecordingPlatform platform = new RecordingPlatform(directory);
        UUID uuid = UUID.randomUUID();
        BanRegistry registry = new BanRegistry(directory, platform);
        registry.put(scoped("VOID-000001", uuid, "Alex", 0L, 1L, "survival"));
        registry.put(scoped("VOID-000002", uuid, "Alex", 0L, 2L, "creative"));

        BanRegistry reloaded = new BanRegistry(directory, platform);
        reloaded.load();

        assertEquals("VOID-000001", reloaded.find(uuid, "Alex", "survival").banId());
        assertEquals("VOID-000002", reloaded.find(uuid, "Alex", "creative").banId());
        assertEquals(List.of("survival"), List.copyOf(reloaded.find(uuid, "Alex", "survival").servers()));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
