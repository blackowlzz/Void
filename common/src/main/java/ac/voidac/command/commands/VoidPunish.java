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

        PlatformPlayer online = VoidAPI.INSTANCE.getPlatformPlayerFactory().getFromName(name);
        if (online != null && !online.isExternalPlayer()) {
            punish(sender, online.getUniqueId(), online.getName(), online, durationArg, reasonArg);
            return;
        }

        OfflinePlatformPlayer offline = VoidAPI.INSTANCE.getPlatformPlayerFactory().getOfflineFromName(name);
        punish(sender, offline.getUniqueId(), offline.getName(), null, durationArg, reasonArg);
    }

    private void punish(Sender sender, UUID playerUuid, String playerName, @Nullable PlatformPlayer onlineTarget,
                        @Nullable String durationArg, @Nullable String reasonArg) {
        if (PunishmentTargetGuard.isSelf(sender, playerUuid, playerName)) {
            sender.sendMessage(error(sc("You cannot punish yourself")));
            return;
        }

        String duration = resolveDuration(sender, durationArg);
        if (duration == null) {
            return;
        }
        String reason = PunishmentReason.resolve(reasonArg, VoidAPI.INSTANCE.getConfigManager().getManualPunishDefaultReason());
        String banId = VoidAPI.INSTANCE.getPunishmentDatabase().reserveBanId();
        String senderName = sender.getName();
        String kickReason = buildKickReason(playerName, senderName, duration, reason, banId);
        String banReason = PunishmentReason.toSingleLine(kickReason, " §8│ §r");
        boolean offline = onlineTarget == null;

        if (onlineTarget != null) {
            VoidAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                    onlineTarget, VoidAPI.INSTANCE.getVoidPlugin(),
                    () -> onlineTarget.kickPlayer(kickReason), null, 0L
            );
        }

        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> {
                    dispatchBan(playerUuid, playerName, kickReason, banReason, duration, banId, offline);
                    VoidAPI.INSTANCE.getPunishmentDatabase().insertWithId(
                            banId, playerUuid, playerName, "manual-punish",
                            senderName, null, banReason, duration, null, null,
                            System.currentTimeMillis()
                    );
                    String outcome = offline ? sc("has been banned") + " &8(" + sc("offline") + ")" : sc("has been banned");
                    sender.sendMessage(success("&f" + playerName + " &7" + outcome));
                    sender.sendMessage(detail("ID", "&d" + banId));
                    sender.sendMessage(detail("Duration", duration));
                    sender.sendMessage(detail("Reason", reason));
                }
        );
    }

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

    private static String buildKickReason(String playerName, String issuedBy, String duration, String reason, String banId) {
        String raw = VoidAPI.INSTANCE.getConfigManager().getManualPunishReason()
                .replace("{player}", playerName)
                .replace("{issued_by}", issuedBy)
                .replace("{duration}", duration)
                .replace("{ban_id}", banId)
                .replace("{date}", PunishmentDatabase.DATE_FORMAT_DISPLAY.format(Instant.now()));
        return MessageUtil.translateAlternateColorCodes('&', PunishmentReason.applyToTemplate(raw, reason));
    }

    private static void dispatchBan(UUID uuid, String name, String kickReason, String banReason, String duration,
                                    String banId, boolean offline) {
        boolean customBan = VoidAPI.INSTANCE.getConfigManager().isManualPunishPreferCustomBan();
        if (customBan) {
            String command = VoidAPI.INSTANCE.getConfigManager().getManualPunishCustomBanCommand()
                    .replace("{player}", name)
                    .replace("{duration}", duration)
                    .replace("{reason}", banReason);
            VoidAPI.INSTANCE.getPlatformServer().dispatchCommand(
                    VoidAPI.INSTANCE.getPlatformServer().getConsoleSender(), command);
        }
        if (!customBan || offline) {
            VoidAPI.INSTANCE.getVoidBanManager().ban(banId, uuid, name, kickReason, duration, System.currentTimeMillis());
        }
    }
}
