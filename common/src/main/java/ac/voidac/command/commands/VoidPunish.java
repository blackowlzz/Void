package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.command.PunishmentTargetGuard;
import ac.voidac.manager.punishment.PunishmentDatabase;
import ac.voidac.manager.punishment.PunishmentDuration;
import ac.voidac.manager.punishment.PunishmentReason;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.player.OfflinePlatformPlayer;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static ac.voidac.command.render.MessageStyle.*;

/**
 * /void punish <player> [duration] [reason]
 *
 * Bans the target with a formatted reason from config.
 * Duration falls back to punishments.manual-punish.duration when omitted.
 * Uses the configured custom-ban-command when prefer-custom-ban is true.
 * Works for both online and offline players.
 */
public class VoidPunish implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .literal("punish")
                        .permission("void.punish")
                        .required("player", StringParser.stringParser(), adapter.onlinePlayerSuggestions())
                        .handler(ctx -> handlePunish(ctx, null, null))
        );

        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .literal("punish")
                        .permission("void.punish")
                        .required("player", StringParser.stringParser(), adapter.onlinePlayerSuggestions())
                        .required("duration", StringParser.stringParser())
                        .optional("reason", StringParser.greedyStringParser())
                        .handler(ctx -> handlePunish(
                                ctx,
                                ctx.getOrDefault("duration", null),
                                ctx.getOrDefault("reason", null)))
        );
    }

    private void handlePunish(@NotNull CommandContext<Sender> context, @Nullable String durationArg,
                              @Nullable String reasonArg) {
        Sender sender = context.sender();
        String name = context.get("player");

        // Try online first: if the player is on this server, kick + ban.
        // Fall through to offline path for players who are not currently connected.
        PlatformPlayer online = VoidAPI.INSTANCE.getPlatformPlayerFactory().getFromName(name);
        if (online != null && !online.isExternalPlayer()) {
            if (rejectSelf(sender, online.getUniqueId(), online.getName())) {
                return;
            }
            punishOnline(sender, online, durationArg, reasonArg);
            return;
        }

        // Player is offline, ban without kicking.
        OfflinePlatformPlayer offline = VoidAPI.INSTANCE.getPlatformPlayerFactory().getOfflineFromName(name);
        if (rejectSelf(sender, offline.getUniqueId(), offline.getName())) {
            return;
        }
        punishOffline(sender, offline, durationArg, reasonArg);
    }

    private void punishOnline(Sender sender, PlatformPlayer platformPlayer, @Nullable String durationArg,
                              @Nullable String reasonArg) {
        String duration = resolveDuration(sender, durationArg);
        if (duration == null) {
            return;
        }
        String reason = resolveReason(reasonArg);
        String banId     = VoidAPI.INSTANCE.getPunishmentDatabase().reserveBanId();
        String dateStr   = PunishmentDatabase.DATE_FORMAT_DISPLAY.format(Instant.now());

        String kickReason = buildKickReason(platformPlayer.getName(), sender.getName(), duration, reason, banId, dateStr);
        String banReason  = toBanCommandLine(kickReason);
        String senderName = sender.getName();
        String playerName = platformPlayer.getName();
        UUID   playerUuid = platformPlayer.getUniqueId();

        // Kick immediately (entity-scheduler for Folia)
        VoidAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                platformPlayer, VoidAPI.INSTANCE.getVoidPlugin(),
                () -> platformPlayer.kickPlayer(kickReason), null, 0L
        );

        // Ban + DB log on the global scheduler
        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> {
                    dispatchBan(playerUuid, playerName, kickReason, banReason, duration, banId, false);
                    VoidAPI.INSTANCE.getPunishmentDatabase().insertWithId(
                            banId, playerUuid, playerName, "manual-punish",
                            senderName, null, banReason, duration, null, null,
                            System.currentTimeMillis()
                    );
                    sendBanSummary(sender, playerName, sc("has been banned"), banId, duration, reason);
                }
        );
    }

    private void punishOffline(Sender sender, OfflinePlatformPlayer player, @Nullable String durationArg,
                               @Nullable String reasonArg) {
        String duration = resolveDuration(sender, durationArg);
        if (duration == null) {
            return;
        }
        String reason = resolveReason(reasonArg);
        String banId      = VoidAPI.INSTANCE.getPunishmentDatabase().reserveBanId();
        String dateStr    = PunishmentDatabase.DATE_FORMAT_DISPLAY.format(Instant.now());
        String senderName = sender.getName();
        String playerName = player.getName();
        UUID   playerUuid = player.getUniqueId();

        String kickReason = buildKickReason(playerName, senderName, duration, reason, banId, dateStr);
        String banReason  = toBanCommandLine(kickReason);

        // Player is offline, no kick. Ban only.
        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> {
                    dispatchBan(playerUuid, playerName, kickReason, banReason, duration, banId, true);
                    VoidAPI.INSTANCE.getPunishmentDatabase().insertWithId(
                            banId, playerUuid, playerName, "manual-punish",
                            senderName, null, banReason, duration, null, null,
                            System.currentTimeMillis()
                    );
                    sendBanSummary(sender, playerName, sc("has been banned") + " &8(" + sc("offline") + ")", banId, duration, reason);
                }
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private @Nullable String resolveDuration(Sender sender, @Nullable String arg) {
        String value = arg != null && !arg.isBlank()
                ? arg
                : VoidAPI.INSTANCE.getConfigManager().getManualPunishDuration();
        try {
            return PunishmentDuration.normalize(value);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(invalidDuration(value));
            return null;
        }
    }

    private boolean rejectSelf(Sender sender, UUID targetUuid, String targetName) {
        if (!PunishmentTargetGuard.isSelf(sender, targetUuid, targetName)) {
            return false;
        }
        sender.sendMessage(error(sc("You cannot punish yourself")));
        return true;
    }

    private static void sendBanSummary(Sender sender, String playerName, String outcome, String banId, String duration, String reason) {
        sender.sendMessage(success("&f" + playerName + " &7" + outcome));
        sender.sendMessage(detail("ID", "&d" + banId));
        sender.sendMessage(detail("Duration", duration));
        sender.sendMessage(detail("Reason", reason));
    }

    private String resolveReason(@Nullable String reasonArg) {
        return PunishmentReason.resolve(
                reasonArg,
                VoidAPI.INSTANCE.getConfigManager().getManualPunishDefaultReason());
    }

    private String buildKickReason(String playerName, String issuedBy, String duration, String reason,
                                   String banId, String dateStr) {
        String raw = VoidAPI.INSTANCE.getConfigManager().getManualPunishReason()
                .replace("{player}",    playerName)
                .replace("{issued_by}", issuedBy)
                .replace("{duration}",  duration)
                .replace("{ban_id}",    banId)
                .replace("{date}",      dateStr);
        raw = PunishmentReason.applyToTemplate(raw, reason);
        return MessageUtil.translateAlternateColorCodes('&', raw);
    }

    private String toBanCommandLine(String rich) {
        return rich.replaceAll("[\\r\\n]+\\s*", " §8│ §r").replaceAll("\\s{2,}", " ").trim();
    }

    private void dispatchBan(UUID uuid, String name, String kickReason, String banReason, String duration, String banId, boolean isOffline) {
        if (VoidAPI.INSTANCE.getConfigManager().isManualPunishPreferCustomBan()) {
            String cmd = VoidAPI.INSTANCE.getConfigManager().getManualPunishCustomBanCommand()
                    .replace("{player}",   name)
                    .replace("{duration}", duration)
                    .replace("{reason}",   banReason);
            VoidAPI.INSTANCE.getPlatformServer().dispatchCommand(
                    VoidAPI.INSTANCE.getPlatformServer().getConsoleSender(), cmd);
            // For offline targets, also store in VoidBanManager: external ban commands
            // may not reliably apply when the player is not currently connected.
            if (isOffline) {
                VoidAPI.INSTANCE.getVoidBanManager().ban(banId, uuid, name, kickReason, duration, System.currentTimeMillis());
            }
        } else {
            VoidAPI.INSTANCE.getVoidBanManager().ban(banId, uuid, name, kickReason, duration, System.currentTimeMillis());
        }
    }
}
