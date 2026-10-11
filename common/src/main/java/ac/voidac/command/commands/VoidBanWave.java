package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.command.PunishmentTargetGuard;
import ac.voidac.manager.BanWaveManager;
import ac.voidac.manager.punishment.PunishmentDuration;
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

import java.util.Map;
import java.util.UUID;

import static ac.voidac.command.render.MessageStyle.*;

public class VoidBanWave implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("info")
                .permission("void.banwave")
                .handler(this::handleInfo));

        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("list")
                .permission("void.banwave")
                .handler(this::handleList));

        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("add")
                .permission("void.banwave")
                .required("player", StringParser.stringParser(), adapter.onlinePlayerSuggestions())
                .handler(ctx -> handleAdd(ctx, null, null)));

        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("add")
                .permission("void.banwave")
                .required("player", StringParser.stringParser(), adapter.onlinePlayerSuggestions())
                .required("duration", StringParser.stringParser())
                .optional("reason", StringParser.greedyStringParser())
                .handler(ctx -> handleAdd(
                        ctx,
                        ctx.getOrDefault("duration", null),
                        ctx.getOrDefault("reason", null))));

        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("remove")
                .permission("void.banwave")
                .required("name", StringParser.stringParser())
                .handler(this::handleRemove));

        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("execute")
                .permission("void.banwave.execute")
                .handler(this::handleExecute));

        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("clear")
                .permission("void.banwave")
                .handler(this::handleClear));
    }

    private void handleInfo(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();
        int next = bwm.getWaveNumber() + 1;

        sender.sendMessage(header("Ban Wave", sc("Status")));
        sender.sendMessage(separator());
        sender.sendMessage(detail("Enabled", enabled(bwm.isEnabled())));
        sender.sendMessage(detail("Queue on punish", enabled(bwm.isQueueOnAutoPunish())));
        sender.sendMessage(detail("Next wave", "&d#" + next));
        sender.sendMessage(detail("Queued players", String.valueOf(bwm.getQueueSize())));
        sender.sendMessage(separator());
    }

    private void handleList(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();
        Map<UUID, BanWaveManager.BanEntry> queue = bwm.getQueue();
        int next = bwm.getWaveNumber() + 1;

        sender.sendMessage(header("Ban Wave", "&d#" + next + " &7" + sc("queue")
                + " &8(&f" + queue.size() + " &7" + sc("players") + "&8)"));

        if (queue.isEmpty()) {
            sender.sendMessage(bullet(sc("Queue is empty")));
            return;
        }

        sender.sendMessage(separator());
        int i = 1;
        for (BanWaveManager.BanEntry entry : queue.values()) {
            sender.sendMessage(MessageUtil.miniMessage(bwm.formatEntry(i++, entry)));
        }
        sender.sendMessage(separator());
    }

    private void handleAdd(@NotNull CommandContext<Sender> context, @Nullable String durationArg,
                           @Nullable String reasonArg) {
        Sender sender = context.sender();
        String name = context.get("player");

        PlatformPlayer online = VoidAPI.INSTANCE.getPlatformPlayerFactory().getFromName(name);
        final UUID uuid;
        final String playerName;

        if (online != null && !online.isExternalPlayer()) {
            uuid = online.getUniqueId();
            playerName = online.getName();
        } else {
            OfflinePlatformPlayer offline = VoidAPI.INSTANCE.getPlatformPlayerFactory().getOfflineFromName(name);
            uuid = offline.getUniqueId();
            playerName = offline.getName();
        }

        if (PunishmentTargetGuard.isSelf(sender, uuid, playerName)) {
            sender.sendMessage(error(sc("You cannot add yourself to a ban wave")));
            return;
        }

        String duration = null;
        if (durationArg != null && !durationArg.isBlank()) {
            try {
                duration = PunishmentDuration.normalize(durationArg);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(invalidDuration(durationArg));
                return;
            }
        }
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();
        String reason = bwm.resolveReason(reasonArg);
        boolean added = bwm.addToQueue(uuid, playerName, "manual", sender.getName(), duration, reason);
        boolean isOffline = online == null || online.isExternalPlayer();

        if (added) {
            String durDisplay = duration != null ? duration : "config default";
            sender.sendMessage(success("&f" + playerName + (isOffline ? " &8(" + sc("offline") + ")" : "")
                    + " &7" + sc("added to wave ") + "&d#" + (bwm.getWaveNumber() + 1)));
            sender.sendMessage(detail("Duration", durDisplay));
            sender.sendMessage(detail("Reason", reason));
            sender.sendMessage(detail("Queue size", String.valueOf(bwm.getQueueSize())));
        } else {
            sender.sendMessage(warning("&f" + playerName + " &7" + sc("is already in the ban wave queue")));
        }
    }

    private void handleRemove(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        String name = context.get("name");
        UUID removed = VoidAPI.INSTANCE.getBanWaveManager().removeFromQueueByName(name);

        if (removed != null) {
            sender.sendMessage(success("&f" + name + " &7" + sc("removed from the ban wave queue")));
        } else {
            sender.sendMessage(error("&f" + name + " &7" + sc("was not found in the queue")));
        }
    }

    private void handleExecute(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();

        if (bwm.getQueueSize() == 0) {
            sender.sendMessage(error(sc("Queue is empty, nothing to execute")));
            return;
        }

        int queued = bwm.getQueueSize();
        int next   = bwm.getWaveNumber() + 1;
        sender.sendMessage(info(sc("Executing ban wave ") + "&d#" + next
                + " &8(&f" + queued + " &7" + sc("players") + "&8)..."));

        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> {
                    int banned = bwm.executeWave();
                    sender.sendMessage(success("&5" + sc("Ban wave") + " &d#" + bwm.getWaveNumber()
                            + " &a" + sc("complete") + " &8─ &f" + banned + " &a" + sc("players banned")));
                }
        );
    }

    private void handleClear(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();
        int count = bwm.getQueueSize();
        bwm.clearQueue();
        sender.sendMessage(success(sc("Queue cleared") + " &8─ &f" + count + " &7" + sc("players removed")));
    }

    private static String enabled(boolean value) {
        return value ? "&a" + sc("yes") : "&c" + sc("no");
    }
}
