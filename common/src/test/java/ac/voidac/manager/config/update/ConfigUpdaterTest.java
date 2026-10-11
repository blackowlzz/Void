package ac.voidac.manager.config.update;

import ac.voidac.VoidAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {

    @TempDir
    Path tempDir;

    @Test
    void addsPunishmentAndViaProxySettingsWithoutReplacingExistingValues() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, """
                via-proxy:
                    allow: true
                    disable-setbacks: false
                punishments:
                    manual-punish:
                        duration: "30d"
                anti-esp:
                    enabled: true
                    replacement-block: "air"
                    underground-reveal-radius: 3
                AntiSpoofA:
                    auto-kick: false
                    kick-message: "<yellow>Custom legacy message"
                config-flavor: V2
                config-version: 22
                """);

        ConfigUpdater updater = new ConfigUpdater(VoidAPI.class, Logger.getLogger("config-updater-test"));
        ConfigUpdater.Result result = updater.update(config.toFile(), VoidConfigSpecs.mainConfig());
        Map<String, Object> updated = new Yaml().load(Files.readString(config));
        Map<String, Object> viaProxy = map(updated.get("via-proxy"));
        Map<String, Object> punishments = map(updated.get("punishments"));
        Map<String, Object> manualPunish = map(punishments.get("manual-punish"));
        Map<String, Object> banWave = map(updated.get("ban-wave"));

        assertTrue(result.migrated);
        assertEquals(36, updated.get("config-version"));
        assertEquals(300, updated.get("max-frozen-client-time"));
        assertEquals(true, updated.get("unverified-dev-debug-greet"));
        assertEquals(true, viaProxy.get("allow"));
        assertEquals(false, viaProxy.get("disable-setbacks"));
        assertEquals(true, updated.get("viaproxy-warn-on-join"));
        assertEquals("unban {player}", punishments.get("unban-command"));
        assertEquals("30d", manualPunish.get("duration"));
        assertEquals("Cheating", manualPunish.get("default-reason"));
        assertEquals("Cheating", banWave.get("default-reason"));
        Map<String, Object> antiEsp = map(updated.get("anti-esp"));
        assertEquals(true, antiEsp.get("enabled"));
        assertEquals(0, antiEsp.get("hide-below-y"));
        assertEquals("stone", antiEsp.get("replacement-block"));
        assertEquals(3, antiEsp.get("underground-reveal-radius"));
        assertEquals(true, antiEsp.get("pov-unmasking"));
        assertEquals(48, antiEsp.get("pov-distance"));
        assertEquals(4, antiEsp.get("pov-refresh-interval-ticks"));
        assertEquals(4, antiEsp.get("chunk-refresh-cooldown-ticks"));
        assertEquals(true, antiEsp.get("terrain-masking"));
        assertEquals(true, antiEsp.get("entity-visibility"));
        assertEquals(true, antiEsp.get("block-entity-visibility"));
        Map<String, Object> antiSpoof = map(updated.get("anti-spoof"));
        assertEquals(false, antiSpoof.get("auto-kick"));
        assertEquals("<yellow>Custom legacy message", antiSpoof.get("kick-message"));
        assertTrue(Files.exists(tempDir.resolve("config.yml.v22.bak")));
        assertFalse(Files.readString(config).contains("\u2014"));
    }

    @Test
    void localizedConfigsExposePunishmentReasonDefaults() throws Exception {
        for (String language : List.of("de", "en", "es", "fr", "it", "ja", "nl", "pl", "pt", "ro", "ru", "tr", "zh")) {
            try (var stream = getClass().getResourceAsStream("/config/" + language + ".yml")) {
                Map<String, Object> config = new Yaml().load(stream);
                Map<String, Object> punishments = map(config.get("punishments"));
                Map<String, Object> manualPunish = map(punishments.get("manual-punish"));
                Map<String, Object> banWave = map(config.get("ban-wave"));
                Map<String, Object> antiEsp = map(config.get("anti-esp"));

                assertEquals(36, config.get("config-version"));
                assertEquals(300, config.get("max-frozen-client-time"));
                assertEquals(true, config.get("unverified-dev-debug-greet"));
                assertEquals("Cheating", manualPunish.get("default-reason"));
                assertEquals("Cheating", banWave.get("default-reason"));
                assertTrue(manualPunish.get("reason").toString().contains("{reason}"));
                assertTrue(banWave.get("reason").toString().contains("{reason}"));
                assertEquals(2, antiEsp.get("underground-reveal-radius"));
                assertEquals(true, antiEsp.get("pov-unmasking"));
                assertEquals(48, antiEsp.get("pov-distance"));
                assertEquals(4, antiEsp.get("pov-refresh-interval-ticks"));
                assertEquals(4, antiEsp.get("chunk-refresh-cooldown-ticks"));
                assertEquals(true, antiEsp.get("terrain-masking"));
                assertEquals(true, antiEsp.get("entity-visibility"));
                assertEquals(true, antiEsp.get("block-entity-visibility"));
                Map<String, Object> antiSpoof = map(config.get("anti-spoof"));
                assertEquals(true, antiSpoof.get("auto-kick"));
                assertTrue(antiSpoof.get("kick-message").toString().contains("blacklisted client"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
