package ac.voidac.manager.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerFileImplTest {

    @Test
    void backfillsCurrentLocalizedFilesWithoutReplacingCustomContent() throws Exception {
        for (String language : List.of("de", "en", "es", "fr", "it", "ja", "nl", "pl", "pt", "ro", "ru", "tr", "zh")) {
            String defaults;
            try (var stream = getClass().getResourceAsStream("/messages/" + language + ".yml")) {
                defaults = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            String input = "# Custom server messages\r\nconfig-version: 2\r\nconfig-flavor: V2\r\n"
                    + "reloaded: Custom reload message\r\ncustom-extension: preserved\r\n";
            List<String> keys = List.of("verbose-join-reminder", "reloading", "reloaded", "reload-failed");
            String output = ConfigManagerFileImpl.ensureMissingRootDefaults(input, defaults, keys);
            Map<?, ?> parsed = new Yaml().load(output);
            Map<?, ?> bundled = new Yaml().load(defaults);

            assertTrue(output.startsWith(input));
            assertEquals("preserved", parsed.get("custom-extension"));
            assertEquals("Custom reload message", parsed.get("reloaded"));
            assertEquals(bundled.get("verbose-join-reminder"), parsed.get("verbose-join-reminder"));
            assertEquals(bundled.get("reloading"), parsed.get("reloading"));
            assertEquals(bundled.get("reload-failed"), parsed.get("reload-failed"));
            assertEquals(output, ConfigManagerFileImpl.ensureMissingRootDefaults(output, defaults, keys));
            assertFalse(output.replace("\r\n", "").contains("\n"));
        }
    }

    @Test
    void backfillsDebugSettingWithoutResettingOptionalChecks() {
        String input = "config-version: 30\nconfig-flavor: V2\nWallHit:\n  block-impossible-hits: false\n";
        String defaults = "debug-packet-exceptions: false\n";
        List<String> keys = List.of("debug-packet-exceptions");
        String output = ConfigManagerFileImpl.ensureMissingRootDefaults(input, defaults, keys);
        Map<?, ?> parsed = new Yaml().load(output);

        assertTrue(output.startsWith(input));
        assertEquals(false, parsed.get("debug-packet-exceptions"));
        assertEquals(Map.of("block-impossible-hits", false), parsed.get("WallHit"));
        String customized = input + "debug-packet-exceptions: true\n";
        assertEquals(customized, ConfigManagerFileImpl.ensureMissingRootDefaults(customized, defaults, keys));
    }

    @Test
    void backfillPreservesEmptyValuesAndRejectsUnsafeDocuments() {
        String defaults = "verbose-join-reminder: default\n";
        List<String> keys = List.of("verbose-join-reminder");
        for (String input : List.of("verbose-join-reminder: ''\n", "verbose-join-reminder: null\n",
                "config-flavor: V3\n", "broken: [\n", "key: first\nkey: duplicate\n", "key: value\n...\n")) {
            assertEquals(input, ConfigManagerFileImpl.ensureMissingRootDefaults(input, defaults, keys));
        }
    }

    @Test
    void addsWallHitWithoutChangingExistingCommandsOrOtherRoots() {
        String input = "Punishments:\r\n  Custom:\r\n    checks: [Reach]\r\n"
                + "    commands: ['7:0 custom %player%']\r\nextra: retained\r\n";

        String output = ConfigManagerFileImpl.ensureWallHitPunishments(input);
        Map<?, ?> parsed = new Yaml().load(output);
        Map<?, ?> groups = (Map<?, ?>) parsed.get("Punishments");

        assertTrue(groups.containsKey("WallHit"));
        assertEquals("retained", parsed.get("extra"));
        assertTrue(output.contains("    commands: ['7:0 custom %player%']\r\n"));
        assertEquals(output, ConfigManagerFileImpl.ensureWallHitPunishments(output));
    }

    @Test
    void preservesCustomWallHitGroupsAndExplicitExclusions() {
        for (String checks : new String[]{"[WallHit]", "['!WallHit']", "[Wall]", "['']"}) {
            String input = "Punishments:\n  Custom:\n    checks: " + checks + "\n    commands: []\n";
            assertEquals(input, ConfigManagerFileImpl.ensureWallHitPunishments(input));
        }
        String disabled = "Punishments:\n  WallHit:\n    checks: []\n    commands: []\n";
        assertEquals(disabled, ConfigManagerFileImpl.ensureWallHitPunishments(disabled));
    }

    @Test
    void leavesMalformedOrUnrelatedPunishmentDocumentsAlone() {
        for (String input : new String[]{"alerts: true\n", "Punishments: [", "Punishments: null\n"}) {
            assertEquals(input, ConfigManagerFileImpl.ensureWallHitPunishments(input));
        }
    }

    @Test
    void removesOnlyTheLegacyAntiSpoofKickCommand() {
        String input = """
                  AntiSpoof:
                    commands:
                      - "1:0 [alert]"
                      - "1:0 kick %player% &cBlacklisted client detected"
                      - "2:0 ban %player% custom reason"
                """;

        String migrated = ConfigManagerFileImpl.migrateAntiSpoofPunishmentConfig(input);

        assertFalse(migrated.contains("1:0 kick %player% &cBlacklisted client detected"));
        assertTrue(migrated.contains("1:0 [alert]"));
        assertTrue(migrated.contains("2:0 ban %player% custom reason"));
    }
}
