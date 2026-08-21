package ac.voidac.manager.config;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.utils.anticheat.LogUtil;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/*
 * This is to hold whatever config manager was set via the reload method in the API
 * and any global variables that are the same between players.
 */
public class BaseConfigManager {

    private final List<Pattern> ignoredClientPatterns = new ArrayList<>();
    @Getter
    private ConfigManager config = null;
    @Getter
    private boolean printAlertsToConsole = false;
    @Getter
    private String prefix = "&bVoid &8»";
    @Getter
    private String webhookNotEnabled;
    @Getter
    private String webhookTestMessage;
    @Getter
    private String webhookTestSucceeded;
    @Getter
    private String webhookTestFailed;
    @Getter
    private String disconnectTimeout;
    @Getter
    private String disconnectClosed;
    @Getter
    private String disconnectPacketError;
    @Getter
    private String disconnectBlacklistedForge;
    @Getter
    private boolean blockBlacklistedForgeClients;
    @Getter
    private boolean disablePongCancelling;
    @Getter
    private boolean autoTotemPunishEnabled;
    @Getter
    private String autoTotemPunishAction;
    @Getter
    private String autoTotemPunishReason;
    @Getter
    private String autoTotemCustomBanDuration;
    @Getter
    private boolean autoTotemPreferCustomBan;
    @Getter
    private String autoTotemCustomBanCommand;
    @Getter
    private boolean autoPunishEnabled;
    @Getter
    private int autoPunishThreshold;
    // Per-check overrides of autoPunishThreshold, keyed by lowercased check config name.
    // Checks flag at wildly different rates: Simulation racks up violations on
    // perfectly legit players, while a Reach flag is already damning. One global
    // number cannot serve both, so each check gets to name its own price.
    private Map<String, Integer> autoPunishThresholds = Collections.emptyMap();

    // Lower bounds for the checks that flag honest players as a matter of course.
    // These are floors, not defaults: a higher global or explicit value still wins.
    // Values sit above each check's alert threshold in punishments.yml, on the theory
    // that if it isn't yet worth telling staff about, it certainly isn't worth a ban.
    private static final Map<String, Integer> DEFAULT_THRESHOLD_FLOORS = Map.ofEntries(
            Map.entry("simulation", 200),
            Map.entry("groundspoof", 150),
            Map.entry("nofall", 150),
            Map.entry("phase", 100),
            Map.entry("timer", 100),
            Map.entry("knockback", 80),
            Map.entry("explosion", 80),
            Map.entry("noslow", 80),
            Map.entry("post", 80),
            Map.entry("reach", 25),
            Map.entry("hitboxes", 25)
    );
    @Getter
    private String autoPunishAction;
    @Getter
    private String autoPunishReason;
    @Getter
    private String autoPunishBroadcastMessage;
    @Getter
    private String autoPunishSupportLink;
    @Getter
    private String autoPunishCustomBanDuration;
    @Getter
    private boolean autoPunishPreferCustomBan;
    @Getter
    private String autoPunishCustomBanCommand;

    // manual /void punish defaults
    @Getter
    private String manualPunishDuration;
    @Getter
    private String manualPunishReason;
    @Getter
    private boolean manualPunishPreferCustomBan;
    @Getter
    private String manualPunishCustomBanCommand;

    // /void optimizer feature toggle: commands refuse to run when false
    @Getter
    private boolean thresholdOptimizerEnabled;

    // Optional Pastebin API key for /gl uploads. Empty = save locally.
    @Getter
    private String pastebinApiKey;

    // initialize the config
    public void load(ConfigManager config) {
        this.config = config;

        int configuredMaxTransactionTime = config.getIntElse("max-transaction-time", 60);
        if (configuredMaxTransactionTime > 180 || configuredMaxTransactionTime < 1) {
            LogUtil.warn("Detected invalid max-transaction-time! This setting is clamped between 1 and 180 to prevent issues. Attempting to disable or set this too high can result in memory usage issues.");
        }

        ignoredClientPatterns.clear();
        for (String string : config.getStringListElse("client-brand.ignored-clients", List.of())) {
            try {
                ignoredClientPatterns.add(Pattern.compile(string));
            } catch (PatternSyntaxException e) {
                throw new RuntimeException("Failed to compile client pattern", e);
            }
        }

        printAlertsToConsole = config.getBooleanElse("alerts.print-to-console", true);
        prefix = config.getStringElse("prefix", "&8[&5Void&8]");

        webhookNotEnabled = config.getStringElse("webhook-not-enabled", "Void webhooks are dormant.");
        webhookTestMessage = config.getStringElse("webhook-test-message", "void signal test");
        webhookTestSucceeded = config.getStringElse("webhook-test-succeeded", "Void signal delivered.");
        webhookTestFailed = config.getStringElse("webhook-test-failed", "Void signal faded.");
        disconnectTimeout = config.getStringElse("disconnect.timeout", "<lang:disconnect.timeout>");
        disconnectClosed = config.getStringElse("disconnect.closed", "<lang:disconnect.timeout>");
        disconnectPacketError = config.getStringElse("disconnect.error", "<red>The void stumbled while processing packets. Please contact the administrators.");
        blockBlacklistedForgeClients = config.getBooleanElse("client-brand.disconnect-blacklisted-forge-versions", true);
        disconnectBlacklistedForge = config.getStringElse("disconnect.blacklisted-forge",
                "<red>Your Forge version is blacklisted because it carries built-in reach exploits.<newline><gold>Versions affected: 1.18.2-1.19.3<newline><newline><red>Please see https://github.com/MinecraftForge/MinecraftForge/issues/9309.");
        disablePongCancelling = config.getBooleanElse("disable-pong-cancelling", false);

        autoTotemPunishEnabled = config.getBooleanElse("exploit.auto-totem.autopunish.enabled", false);
        autoTotemPunishAction = config.getStringElse("exploit.auto-totem.autopunish.action", "ban");
        autoTotemPunishReason = config.getStringElse("exploit.auto-totem.autopunish.reason", "&cAutoTotem detected.\n&7Check: &f%check_name% &8(&dVL %vl%&8)");
        autoTotemCustomBanDuration = config.getStringElse("exploit.auto-totem.autopunish.duration", "1d");
        autoTotemPreferCustomBan = config.getBooleanElse("exploit.auto-totem.autopunish.prefer-custom-ban", false);
        autoTotemCustomBanCommand = config.getStringElse("exploit.auto-totem.autopunish.custom-ban-command", "litebans:tempban {player} {duration} {reason}");

        autoPunishEnabled = config.getBooleanElse("punishments.auto-punish.enabled", autoTotemPunishEnabled);
        autoPunishThreshold = config.getIntElse("punishments.auto-punish.threshold", 100);
        autoPunishThresholds = loadAutoPunishThresholds(config);
        autoPunishAction = config.getStringElse("punishments.auto-punish.action", autoTotemPunishAction);
        autoPunishReason = config.getStringElse("punishments.auto-punish.reason", "&cThe void has claimed you.\n&7Check: &f%check_name% &8(&dVL %vl%&8)\n&7Why? You were detected cheating or bypassing check logic.\n&7If this was a mistake, check the appeal link below.");
        autoPunishBroadcastMessage = config.getStringElse("punishments.auto-punish.broadcast-message", "&8[&5Void&8] &5The Void&7 punished &f%player%&7 for &f%check_name%&7 &8(&dVL %vl%&8)");
        autoPunishSupportLink = config.getStringElse("punishments.auto-punish.support-link", "");
        autoPunishCustomBanDuration = config.getStringElse("punishments.auto-punish.duration", autoTotemCustomBanDuration);
        autoPunishPreferCustomBan = config.getBooleanElse("punishments.auto-punish.prefer-custom-ban", autoTotemPreferCustomBan);
        autoPunishCustomBanCommand = config.getStringElse("punishments.auto-punish.custom-ban-command", autoTotemCustomBanCommand);

        manualPunishDuration = config.getStringElse("punishments.manual-punish.duration", "permanent");
        manualPunishReason   = config.getStringElse("punishments.manual-punish.reason",
                "&5&l✦ Void AntiCheat\n&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n&c&lYou have been banned.\n&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n&7 Banned by &8» &f{issued_by}\n&7 Duration  &8» &f{duration}\n&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        manualPunishPreferCustomBan = config.getBooleanElse("punishments.manual-punish.prefer-custom-ban", autoPunishPreferCustomBan);
        manualPunishCustomBanCommand = config.getStringElse("punishments.manual-punish.custom-ban-command", autoPunishCustomBanCommand);

        thresholdOptimizerEnabled = config.getBooleanElse("threshold-optimizer.enabled", false);

        pastebinApiKey = config.getStringElse("pastebin-api-key", "");
    }

    // ran on start, can be used to handle things that can't be done while loading
    public void start() {
    }

    public boolean isIgnoredClient(String brand) {
        for (Pattern pattern : ignoredClientPatterns) {
            if (pattern.matcher(brand).find()) return true;
        }
        return false;
    }

    /**
     * Violations this specific check must reach before auto-punish fires.
     * <p>
     * Names are matched as substrings of the check's config name, the same way the
     * punishment groups match, so "Timer" covers TimerA, TimerLimit, TickTimer and
     * friends. When several entries match, the most specific (longest) one wins.
     * <p>
     * Checks in {@link #DEFAULT_THRESHOLD_FLOORS} also get a built-in floor, so a
     * config that predates this option (or a global threshold tuned for a decisive
     * check like Reach) can't ban honest players off a handful of Simulation flags.
     * An explicit entry in the config always overrides that floor.
     */
    public int getAutoPunishThresholdFor(String configName) {
        int threshold = autoPunishThreshold;
        if (configName == null || configName.isEmpty()) return threshold;
        String name = configName.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Integer> floor : DEFAULT_THRESHOLD_FLOORS.entrySet()) {
            if (name.contains(floor.getKey())) {
                threshold = Math.max(threshold, floor.getValue());
            }
        }

        Integer override = null;
        int bestMatch = -1;
        for (Map.Entry<String, Integer> entry : autoPunishThresholds.entrySet()) {
            if (name.contains(entry.getKey()) && entry.getKey().length() > bestMatch) {
                override = entry.getValue();
                bestMatch = entry.getKey().length();
            }
        }

        return override != null ? override : threshold;
    }

    private Map<String, Integer> loadAutoPunishThresholds(ConfigManager config) {
        Map<String, Object> raw = config.getMapElse("punishments.auto-punish.thresholds", Collections.emptyMap());
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();

        Map<String, Integer> parsed = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            try {
                int value = entry.getValue() instanceof Number number
                        ? number.intValue()
                        : Integer.parseInt(entry.getValue().toString().trim());
                // A zero or negative override would ban on the very first flag; refuse it.
                if (value > 0) parsed.put(entry.getKey().toLowerCase(Locale.ROOT), value);
                else LogUtil.warn("Ignoring auto-punish threshold for '" + entry.getKey() + "': must be greater than 0.");
            } catch (NumberFormatException e) {
                LogUtil.warn("Ignoring auto-punish threshold for '" + entry.getKey() + "': '" + entry.getValue() + "' is not a number.");
            }
        }
        return parsed;
    }
}
