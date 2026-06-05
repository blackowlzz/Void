package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.manager.punishment.PunishmentDatabase;
import ac.voidac.platform.api.command.PlayerSelector;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
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

/**
 * /void punish <player> [duration]
 *
 * Bans the target with a formatted reason from config.
 * Duration falls back to punishments.manual-punish.duration when omitted.
 * Uses the configured custom-ban-command when prefer-custom-ban is true.
 */
public class VoidPunish implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .literal("punish")
                        .permission("void.punish")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .handler(ctx -> handlePunish(ctx, null))
        );

        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .literal("punish")
                        .permission("void.punish")
                        .required("target", adapter.singlePlayerSelectorParser())
                        .required("duration", StringParser.stringParser())
                        .handler(ctx -> handlePunish(ctx, ctx.get("duration")))
        );
    }

    private void handlePunish(@NotNull CommandContext<Sender> context, @Nullable String durationArg) {
        Sender sender = context.sender();
        PlayerSelector target = context.get("target");
        PlatformPlayer platformPlayer = target.getSinglePlayer().getPlatformPlayer();

        // Resolve the target purely from the platform player — exactly like /void banwave does.
        // We deliberately do NOT look the player up in PlayerDataManager: that lookup goes
        // UUID -> PacketEvents channel -> User, which fails on offline-mode / hybrid servers
        // (the offline UUID isn't indexed by PacketEvents) and also returns null for exempt
        // players. The ban only needs the player's name + UUID, both of which we already have.
        if (platformPlayer == null || platformPlayer.isExternalPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-this-server",
                    "%prefix% &cThat player is not on this server."));
            return;
        }

        String duration  = resolveDuration(durationArg);

        // Reserve the ban ID before building the kick reason so it can appear on the disconnect screen
        String banId     = VoidAPI.INSTANCE.getPunishmentDatabase().reserveBanId();
        String dateStr   = PunishmentDatabase.DATE_FORMAT_DISPLAY.format(Instant.now());

        String kickReason = buildKickReason(platformPlayer.getName(), sender.getName(), duration, banId, dateStr);
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
                    dispatchBan(playerUuid, playerName, kickReason, banReason, duration, banId);
                    VoidAPI.INSTANCE.getPunishmentDatabase().insertWithId(
                            banId,
                            playerUuid,
                            playerName,
                            "manual-punish",
                            senderName,
                            null,
                            banReason,
                            duration,
                            null,
                            null,
                            System.currentTimeMillis()
                    );
                    sender.sendMessage(MessageUtil.miniMessage(
                            "&8[&5Void&8] &a✔ &f" + playerName
                                    + " &7has been banned."
                                    + " &8(&7ID&8: &d" + banId
                                    + " &8│ &7duration&8: &f" + duration + "&8)"));
                }
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String resolveDuration(@Nullable String arg) {
        return (arg != null && !arg.isBlank()) ? arg.trim()
                : VoidAPI.INSTANCE.getConfigManager().getManualPunishDuration();
    }

    private String buildKickReason(String playerName, String issuedBy, String duration, String banId, String dateStr) {
        String raw = VoidAPI.INSTANCE.getConfigManager().getManualPunishReason()
                .replace("{player}",    playerName)
                .replace("{issued_by}", issuedBy)
                .replace("{duration}",  duration)
                .replace("{ban_id}",    banId)
                .replace("{date}",      dateStr);
        return MessageUtil.translateAlternateColorCodes('&', raw);
    }

    private String toBanCommandLine(String rich) {
        return rich.replaceAll("[\\r\\n]+\\s*", " §8│ §r").replaceAll("\\s{2,}", " ").trim();
    }

    private void dispatchBan(UUID uuid, String name, String kickReason, String banReason, String duration, String banId) {
        if (VoidAPI.INSTANCE.getConfigManager().isManualPunishPreferCustomBan()) {
            String cmd = VoidAPI.INSTANCE.getConfigManager().getManualPunishCustomBanCommand()
                    .replace("{player}",   name)
                    .replace("{duration}", duration)
                    .replace("{reason}",   banReason);
            VoidAPI.INSTANCE.getPlatformServer().dispatchCommand(
                    VoidAPI.INSTANCE.getPlatformServer().getConsoleSender(), cmd);
        } else {
            VoidAPI.INSTANCE.getVoidBanManager().ban(banId, uuid, name, kickReason, duration, System.currentTimeMillis());
        }
    }
}
