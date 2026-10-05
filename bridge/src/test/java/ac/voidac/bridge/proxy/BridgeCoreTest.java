package ac.voidac.bridge.proxy;

import ac.voidac.bridge.protocol.BridgeFrame;
import ac.voidac.bridge.protocol.MessageType;
import ac.voidac.bridge.protocol.payload.BanPayload;
import ac.voidac.bridge.protocol.payload.UnbanPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeCoreTest {

    @TempDir
    Path directory;

    private RecordingPlatform platform;
    private BanRegistry registry;
    private BridgeCore core;

    @BeforeEach
    void setUp() {
        platform = new RecordingPlatform(directory, "survival", "creative");
        BridgeConfig config = BridgeConfig.load(directory, platform);
        assertNotNull(config);
        registry = new BanRegistry(directory, platform);
        core = new BridgeCore(platform, config, registry);
    }

    private static BridgeFrame banFrame(String origin, UUID uuid, String name) {
        BanPayload ban = new BanPayload("VOID-000001", uuid, name, "cheating", 0L, System.currentTimeMillis());
        return new BridgeFrame(MessageType.BAN, origin, System.currentTimeMillis(), ban.encode());
    }

    @Test
    void banFromTheAuthenticatedBackendIsStored() {
        UUID uuid = UUID.randomUUID();
        core.handleFrame("survival", banFrame("survival", uuid, "Alex"));

        assertNotNull(registry.find(uuid, "Alex", null));
        assertTrue(platform.disconnected.contains(uuid));
    }

    @Test
    void originIsComparedIgnoringCase() {
        UUID uuid = UUID.randomUUID();
        core.handleFrame("survival", banFrame("Survival", uuid, "Alex"));

        assertNotNull(registry.find(uuid, "Alex", null));
    }

    @Test
    void banClaimingAnotherOriginIsIgnored() {
        UUID uuid = UUID.randomUUID();
        core.handleFrame("creative", banFrame("survival", uuid, "Alex"));

        assertNull(registry.find(uuid, "Alex", null));
        assertTrue(platform.disconnected.isEmpty());
        assertEquals(1, platform.warnings.size());
    }

    @Test
    void unbanClaimingAnotherOriginIsIgnored() {
        UUID uuid = UUID.randomUUID();
        core.handleFrame("survival", banFrame("survival", uuid, "Alex"));

        UnbanPayload unban = new UnbanPayload(uuid, "Alex", "VOID-000001");
        core.handleFrame("creative", new BridgeFrame(
                MessageType.UNBAN, "survival", System.currentTimeMillis(), unban.encode()));

        assertNotNull(registry.find(uuid, "Alex", null));
        assertFalse(platform.warnings.isEmpty());
    }
}
