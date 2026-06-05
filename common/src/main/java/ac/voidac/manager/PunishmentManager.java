package ac.voidac.manager;

import ac.voidac.VoidAPI;
import ac.voidac.api.AbstractCheck;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.manager.BanWaveManager;
import ac.voidac.api.config.ConfigReloadable;
import ac.voidac.api.event.events.CommandExecuteEvent;
import ac.voidac.checks.Check;
import ac.voidac.events.packets.ProxyAlertMessenger;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import ac.voidac.manager.punishment.PunishmentDatabase;
import java.time.Instant;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PunishmentManager implements ConfigReloadable {
    private final VoidPlayer player;
    private final List<PunishGroup> groups = new ArrayList<>();
    private final Map<Check, Long> autoPunishHistory = new HashMap<>();
    private String experimentalSymbol = "*";
    private String alertString;
    private boolean testMode;
    private String proxyAlertString = "";
    private static final CommandExecuteEvent.Channel COMMAND_CHANNEL = VoidAPI.INSTANCE.getEventBus().get(CommandExecuteEvent.class);

    public PunishmentManager(VoidPlayer player) {
        this.player = player;
    }

    @Override
    public void reload(ConfigManager config) {
        Map<String, Object> punish = config.getMapElse("Punishments", new LinkedHashMap<>());
        experimentalSymbol = config.getStringElse("experimental-symbol", "*");
        alertString = config.getStringElse("alerts-format", "%prefix% &f%player% &5was claimed by &f%check_name%%experimental% &8(&d%vl%&8) &7%verbose%");
        testMode = config.getBooleanElse("test-mode", false);
        proxyAlertString = config.getStringElse("alerts-format-proxy", "%prefix% &f[&5proxy&f] &f%player% &5was claimed by &f%check_name%%experimental% &8(&d%vl%&8) &7%verbose%");
        autoPunishHistory.clear();
        try {
            groups.clear();
            int enabledChecks = 0;

            // To support reloading
            for (AbstractCheck check : player.checkManager.allChecks.values()) {
                check.setEnabled(false);
            }

            for (Object s : punish.values()) {
                Map<String, Object> map = (Map<String, Object>) s;

                List<String> checks = (List<String>) map.getOrDefault("checks", new ArrayList<>());
                List<String> commands = (List<String>) map.getOrDefault("commands", new ArrayList<>());
                int removeViolationsAfter = (int) map.getOrDefault("remove-violations-after", 300);

                List<ParsedCommand> parsed = new ArrayList<>();
                List<AbstractCheck> checksList = new ArrayList<>();
                List<AbstractCheck> excluded = new ArrayList<>();
                for (String command : checks) {
                    command = command.toLowerCase(Locale.ROOT);
                    boolean exclude = false;
                    if (command.startsWith("!")) {
                        exclude = true;
                        command = command.substring(1);
                    }
                    for (AbstractCheck check : player.checkManager.allChecks.values()) { // o(n) * o(n)?
                        if (check.getCheckName() != null &&
                                (check.getCheckName().toLowerCase(Locale.ROOT).contains(command)
                                        || check.getAlternativeName().toLowerCase(Locale.ROOT).contains(command))) { // Some checks have equivalent names like AntiKB and AntiKnockback
                            if (exclude) {
                                excluded.add(check);
                            } else {
                                checksList.add(check);
                                check.setEnabled(true);
                                enabledChecks++;
                            }
                        }
                    }
                    for (AbstractCheck check : excluded) checksList.remove(check);
                }

                for (String command : commands) {
                    String firstNum = command.substring(0, command.indexOf(":"));
                    String secondNum = command.substring(command.indexOf(":"), command.indexOf(" "));

                    int threshold = Integer.parseInt(firstNum);
                    int interval = Integer.parseInt(secondNum.substring(1));
                    String commandString = command.substring(command.indexOf(" ") + 1);

                    parsed.add(new ParsedCommand(threshold, interval, commandString));
                }

                groups.add(new PunishGroup(checksList, parsed, removeViolationsAfter * 1000));
            }

            LogUtil.info("Loaded " + groups.size() + " punishment groups and enabled " + enabledChecks + " checks.");
        } catch (Exception e) {
            LogUtil.error("Error while loading punishments.yml! This is likely your fault!", e);
        }
    }

    private String replaceAlertPlaceholders(String original, int vl, Check check, String verbose) {
        return MessageUtil.replacePlaceholders(player, original
                .replace("[alert]", alertString)
                .replace("[proxy]", proxyAlertString)
                .replace("%check_name%", check.getDisplayName())
                .replace("%experimental%", check.isExperimental() ? experimentalSymbol : "")
                .replace("%vl%", Integer.toString(vl))
                .replace("%description%", check.getDescription())
        ).replace("%verbose%", MiniMessage.miniMessage().escapeTags(verbose));
    }

    public boolean handleAlert(VoidPlayer player, String verbose, Check check) {
        boolean sentDebug = false;

        // Feed every flag to the optimizer when it is running.  No-op when stopped.
        VoidAPI.INSTANCE.getThresholdOptimizer().recordFlag(player, check, (int) Math.floor(check.violations));

        // Check commands
        for (PunishGroup group : groups) {
            if (group.checks.contains(check)) {
                final int vl = getViolations(group, check);
                final int violationCount = group.violations.size();
                for (ParsedCommand command : group.commands) {
                    String cmd = replaceAlertPlaceholders(command.command, vl, check, verbose);

                    @Nullable Set<@Nullable PlatformPlayer> verboseListeners = null;

                    // Verbose that prints all flags
                    if (VoidAPI.INSTANCE.getAlertManager().hasVerboseListeners() && command.command.equals("[alert]")) {
                        sentDebug = true;
                        Component component = MessageUtil.miniMessage(cmd);
                        verboseListeners = VoidAPI.INSTANCE.getAlertManager().sendVerbose(component, null);
                    }

                    if (violationCount >= command.threshold) {
                        // 0 means execute once
                        // Any other number means execute every X interval
                        boolean inInterval = command.interval == 0 ? (command.executeCount == 0) : (violationCount % command.interval == 0);
                        if (inInterval) {
                            if (COMMAND_CHANNEL.fire(player, check, verbose, cmd)) continue;

                            switch (command.command) {
                                case "[webhook]" -> VoidAPI.INSTANCE.getDiscordManager().sendAlert(player, verbose, check.getDisplayName(), vl);
                                case "[log]" -> {
                                    String verboseWithoutGl = verbose.replaceAll(" /gl .*", "");
                                    VoidAPI.INSTANCE.getDataStoreLifecycle().liveWriteHooks()
                                            .recordFlagFromCheck(player, check, vl, verboseWithoutGl);
                                }
                                case "[proxy]" -> ProxyAlertMessenger.sendPluginMessage(cmd);
                                case "[alert]" -> {
                                    sentDebug = true;
                                    Component message = MessageUtil.miniMessage(cmd);
                                    if (testMode) { // secret test mode
                                        if (verboseListeners == null || verboseListeners.contains(player.platformPlayer)) {
                                            player.sendMessage(message);
                                        }
                                    } else {
                                        VoidAPI.INSTANCE.getAlertManager().sendAlert(message, verboseListeners);
                                    }
                                }
                                default -> VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(VoidAPI.INSTANCE.getVoidPlugin(), () ->
                                        VoidAPI.INSTANCE.getPlatformServer().dispatchCommand(
                                                VoidAPI.INSTANCE.getPlatformServer().getConsoleSender(),
                                                cmd
                                        )
                                );
                            }
                        }

                        command.executeCount++;
                    }
                }
            }
        }

        return sentDebug;
    }

    public void handleAutoPunish(Check check) {
        if (!VoidAPI.INSTANCE.getConfigManager().isAutoPunishEnabled()) {
            return;
        }

        long currentViolations = (long) Math.floor(check.violations);
        long threshold = Math.max(1, VoidAPI.INSTANCE.getConfigManager().getAutoPunishThreshold());
        if (currentViolations < threshold) {
            return;
        }

        Long alreadyPunishedAt = autoPunishHistory.get(check);
        if (alreadyPunishedAt != null) {
            return;
        }

        autoPunishHistory.put(check, currentViolations);

        // If ban-wave queuing is active, park this player in the wave instead of punishing now.
        BanWaveManager banWave = VoidAPI.INSTANCE.getBanWaveManager();
        if (banWave.isEnabled() && banWave.isQueueOnAutoPunish()) {
            banWave.addToQueue(player.uuid, player.getName(), check.getDisplayName(), "auto-punish");
            return;
        }

        String action   = VoidAPI.INSTANCE.getConfigManager().getAutoPunishAction().trim().toLowerCase(Locale.ROOT);
        final String duration = VoidAPI.INSTANCE.getConfigManager().getAutoPunishCustomBanDuration();

        // Reserve the ban ID before formatting the reason so it can appear on the disconnect screen
        String banId = VoidAPI.INSTANCE.getPunishmentDatabase().reserveBanId();
        String dateStr = PunishmentDatabase.DATE_FORMAT_DISPLAY.format(Instant.now());

        String rawReason = replaceAutoPunishPlaceholders(VoidAPI.INSTANCE.getConfigManager().getAutoPunishReason(), check, currentViolations, action);
        if (rawReason == null) rawReason = VoidAPI.INSTANCE.getConfigManager().getAutoPunishReason();
        rawReason = rawReason
                .replace("%ban_id%",  banId)
                .replace("%date%",    dateStr)
                .replace("%duration%", "ban".equals(action) ? duration : "kick");

        String reason = MessageUtil.translateAlternateColorCodes('&', rawReason);
        String plainReason = normalizeCommandReason(MessageUtil.stripColor(reason));
        String supportLink = VoidAPI.INSTANCE.getConfigManager().getAutoPunishSupportLink();
        String supportSuffix = buildSupportSuffix(supportLink);
        String broadcastMessage = replaceAutoPunishPlaceholders(VoidAPI.INSTANCE.getConfigManager().getAutoPunishBroadcastMessage(), check, currentViolations, action, null);

        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(VoidAPI.INSTANCE.getVoidPlugin(), () -> {
            broadcastAutoPunish(broadcastMessage);
            executeAutoPunish(action, reason, plainReason, supportSuffix, banId, duration);
            logAutoPunishToDb(check, currentViolations, plainReason, action, duration, banId);
        });
    }

    private void logAutoPunishToDb(Check check, long vl, String reason, String action, String duration, String banId) {
        String flags = buildFlagsSummary();
        String effectiveDuration = "ban".equals(action) ? duration : "kick";
        VoidAPI.INSTANCE.getPunishmentDatabase().insert(
                player.uuid,
                player.getName(),
                "auto-punish",
                "void-anticheat",
                check.getDisplayName(),
                reason,
                effectiveDuration,
                null,
                flags,
                System.currentTimeMillis()
        );
    }

    private String buildFlagsSummary() {
        StringBuilder sb = new StringBuilder();
        for (AbstractCheck abstractCheck : player.checkManager.allChecks.values()) {
            if (abstractCheck instanceof Check c && c.violations >= 1.0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(c.getDisplayName()).append(": ").append((int) Math.floor(c.violations));
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String replaceAutoPunishPlaceholders(String template, Check check, long violations, String action) {
        return replaceAutoPunishPlaceholders(template, check, violations, action, null);
    }

    private String replaceAutoPunishPlaceholders(String template, Check check, long violations, String action, @Nullable String reason) {
        String message = MessageUtil.replacePlaceholders(player, template);
        if (message == null) {
            return null;
        }

        message = message
                .replace("%check_name%", check.getDisplayName())
                .replace("%vl%", Long.toString(violations))
                .replace("%action%", action);

        if (reason != null) {
            message = message.replace("%reason%", reason);
        }

        return message;
    }

    private void broadcastAutoPunish(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        Component component = MessageUtil.miniMessage(message);
        for (PlatformPlayer onlinePlayer : VoidAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers()) {
            onlinePlayer.sendMessage(component);
        }
    }

    private String buildSupportSuffix(String supportLink) {
        if (supportLink == null || supportLink.isBlank()) {
            return "";
        }

        return "\n" + MessageUtil.translateAlternateColorCodes('&', "&7Appeal your ban: &b" + supportLink);
    }

    private String normalizeCommandReason(@Nullable String reason) {
        if (reason == null) {
            return "";
        }

        return reason.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private void executeAutoPunish(String action, String reason, String plainReason, String supportSuffix,
                                   String banId, String duration) {
        String punishReason = reason + supportSuffix;
        String plainPunishReason = plainReason + (supportSuffix.isBlank() ? "" : " | Appeal your ban: " + VoidAPI.INSTANCE.getConfigManager().getAutoPunishSupportLink());

        if ("kick".equals(action)) {
            if (player.platformPlayer != null) {
                VoidAPI.INSTANCE.getScheduler().getEntityScheduler().execute(player.platformPlayer, VoidAPI.INSTANCE.getVoidPlugin(),
                        () -> player.platformPlayer.kickPlayer(punishReason), null, 0L);
            } else {
                dispatchCommand("kick " + player.getName() + " " + plainPunishReason);
            }
            return;
        }

        if (VoidAPI.INSTANCE.getConfigManager().isAutoPunishPreferCustomBan()) {
            String cmd = VoidAPI.INSTANCE.getConfigManager().getAutoPunishCustomBanCommand()
                    .replace("{player}",   player.getName())
                    .replace("{duration}", VoidAPI.INSTANCE.getConfigManager().getAutoPunishCustomBanDuration())
                    .replace("{reason}",   plainPunishReason);
            dispatchCommand(cmd);
        } else {
            if (player.platformPlayer != null) {
                VoidAPI.INSTANCE.getScheduler().getEntityScheduler().execute(player.platformPlayer, VoidAPI.INSTANCE.getVoidPlugin(),
                        () -> player.platformPlayer.kickPlayer(punishReason), null, 0L);
            }
            VoidAPI.INSTANCE.getVoidBanManager().ban(banId, player.uuid, player.getName(), punishReason, duration, System.currentTimeMillis());
        }
    }

    private void dispatchCommand(String command) {
        VoidAPI.INSTANCE.getPlatformServer().dispatchCommand(VoidAPI.INSTANCE.getPlatformServer().getConsoleSender(), command);
    }

    public void handleViolation(Check check) {
        for (PunishGroup group : groups) {
            if (group.checks.contains(check)) {
                long currentTime = System.currentTimeMillis();

                group.violations.put(currentTime, check);
                // Remove violations older than the defined time in the config
                group.violations.long2ObjectEntrySet().removeIf(time -> currentTime - time.getLongKey() > group.removeViolationsAfter);
            }
        }
    }

    private int getViolations(PunishGroup group, Check check) {
        int vl = 0;
        for (Check value : group.violations.values()) {
            if (value == check) vl++;
        }
        return vl;
    }
}

@RequiredArgsConstructor
class PunishGroup {
    public final List<AbstractCheck> checks;
    public final List<ParsedCommand> commands;
    public final Long2ObjectMap<Check> violations = new Long2ObjectOpenHashMap<>();
    public final int removeViolationsAfter; // time to remove violations after in milliseconds
}

@RequiredArgsConstructor
class ParsedCommand {
    public final int threshold;
    public final int interval;
    public final String command;
    public int executeCount;
}
