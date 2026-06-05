package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.manager.BanWaveManager;
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

import java.util.Map;
import java.util.UUID;

public class VoidBanWave implements BuildableCommand {

    // ── Reusable style constants ──────────────────────────────────────────
    private static final String PREFIX  = "&8[&5Void&8]";
    private static final String SEP     = "&8 &m─────────────────────────────────";
    private static final String DIVIDER = "&8│";

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

        // /void banwave add <player>
        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("add")
                .permission("void.banwave")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(ctx -> handleAdd(ctx, null)));

        // /void banwave add <player> <duration>
        commandManager.command(commandManager.commandBuilder("void", "voidac")
                .literal("banwave").literal("add")
                .permission("void.banwave")
                .required("target", adapter.singlePlayerSelectorParser())
                .required("duration", StringParser.stringParser())
                .handler(ctx -> handleAdd(ctx, ctx.get("duration"))));

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

    // ── Handlers ──────────────────────────────────────────────────────────

    private void handleInfo(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();
        int next = bwm.getWaveNumber() + 1;

        sender.sendMessage(MessageUtil.miniMessage(PREFIX + " &5Ban Wave &7─ Status"));
        sender.sendMessage(MessageUtil.miniMessage(SEP));
        sender.sendMessage(MessageUtil.miniMessage("  &8▸ &7Enabled          " + DIVIDER + " " + bool(bwm.isEnabled())));
        sender.sendMessage(MessageUtil.miniMessage("  &8▸ &7Queue on punish  " + DIVIDER + " " + bool(bwm.isQueueOnAutoPunish())));
        sender.sendMessage(MessageUtil.miniMessage("  &8▸ &7Next wave        " + DIVIDER + " &d#" + next));
        sender.sendMessage(MessageUtil.miniMessage("  &8▸ &7Queued players   " + DIVIDER + " &f" + bwm.getQueueSize()));
        sender.sendMessage(MessageUtil.miniMessage(SEP));
    }

    private void handleList(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();
        Map<UUID, BanWaveManager.BanEntry> queue = bwm.getQueue();
        int next = bwm.getWaveNumber() + 1;

        sender.sendMessage(MessageUtil.miniMessage(
                PREFIX + " &5Ban Wave &d#" + next
                        + " &7queue &8(&f" + queue.size() + " &7players&8):"));

        if (queue.isEmpty()) {
            sender.sendMessage(MessageUtil.miniMessage("  &8▸ &7Queue is empty."));
            return;
        }

        sender.sendMessage(MessageUtil.miniMessage(SEP));
        int i = 1;
        for (BanWaveManager.BanEntry entry : queue.values()) {
            sender.sendMessage(MessageUtil.miniMessage(bwm.formatEntry(i++, entry)));
        }
        sender.sendMessage(MessageUtil.miniMessage(SEP));
    }

    private void handleAdd(@NotNull CommandContext<Sender> context, @Nullable String durationArg) {
        Sender sender = context.sender();
        PlayerSelector target = context.get("target");
        PlatformPlayer pp = target.getSinglePlayer().getPlatformPlayer();

        if (pp == null || pp.isExternalPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-this-server",
                    "%prefix% &cThat player is not on this server."));
            return;
        }

        String duration = (durationArg != null && !durationArg.isBlank()) ? durationArg.trim() : null;
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();
        boolean added = bwm.addToQueue(pp.getUniqueId(), pp.getName(), "manual", sender.getName(), duration);

        if (added) {
            String durDisplay = duration != null ? duration : "config default (" + VoidAPI.INSTANCE.getBanWaveManager() + ")";
            if (duration == null) durDisplay = "config default";
            sender.sendMessage(MessageUtil.miniMessage(
                    PREFIX + " &a✔ &f" + pp.getName()
                            + " &7added to wave &d#" + (bwm.getWaveNumber() + 1)
                            + " &8(&7duration&8: &f" + durDisplay
                            + " &8│ &7total&8: &f" + bwm.getQueueSize() + "&8)."));
        } else {
            sender.sendMessage(MessageUtil.miniMessage(
                    PREFIX + " &e⚠ &f" + pp.getName() + " &7is already in the ban wave queue."));
        }
    }

    private void handleRemove(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        String name = context.get("name");
        UUID removed = VoidAPI.INSTANCE.getBanWaveManager().removeFromQueueByName(name);

        if (removed != null) {
            sender.sendMessage(MessageUtil.miniMessage(
                    PREFIX + " &a✔ &f" + name + " &7removed from the ban wave queue."));
        } else {
            sender.sendMessage(MessageUtil.miniMessage(
                    PREFIX + " &c✖ &f" + name + " &7was not found in the queue."));
        }
    }

    private void handleExecute(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();

        if (bwm.getQueueSize() == 0) {
            sender.sendMessage(MessageUtil.miniMessage(
                    PREFIX + " &c✖ &7Queue is empty — nothing to execute."));
            return;
        }

        int queued = bwm.getQueueSize();
        int next   = bwm.getWaveNumber() + 1;
        sender.sendMessage(MessageUtil.miniMessage(
                PREFIX + " &7Executing ban wave &d#" + next
                        + " &8(&f" + queued + " &7players&8)..."));

        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> {
                    int banned = bwm.executeWave();
                    sender.sendMessage(MessageUtil.miniMessage(
                            PREFIX + " &5⚡ Ban Wave &d#" + bwm.getWaveNumber()
                                    + " &acomplete &8─ &f" + banned + " &aplayers banned."));
                }
        );
    }

    private void handleClear(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        BanWaveManager bwm = VoidAPI.INSTANCE.getBanWaveManager();
        int count = bwm.getQueueSize();
        bwm.clearQueue();
        sender.sendMessage(MessageUtil.miniMessage(
                PREFIX + " &7Queue cleared &8─ &f" + count + " &7players removed."));
    }

    // ── Util ──────────────────────────────────────────────────────────────

    private static String bool(boolean v) {
        return v ? "&ayes" : "&cno";
    }
}
