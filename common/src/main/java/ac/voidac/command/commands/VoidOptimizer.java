package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.manager.ThresholdOptimizerManager;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

/**
 * /void optimizer &lt;start | stop | save | status | discard&gt;
 *
 * Owner-facing tuner that studies live flag data and recommends safer thresholds.
 * Refuses to run unless threshold-optimizer.enabled is true in config.
 *
 * Permission: void.optimizer
 *
 * See en.yml &gt; "THRESHOLD OPTIMIZER" for the full workflow.
 */
public class VoidOptimizer implements BuildableCommand {

    private static final String PREFIX = "&8[&5Void&8]";
    private static final String SEP    = "&8 &m─────────────────────────────────";

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        for (String sub : new String[]{"start", "stop", "save", "status", "discard"}) {
            commandManager.command(
                    commandManager.commandBuilder("void", "voidac")
                            .literal("optimizer")
                            .literal(sub)
                            .permission("void.optimizer")
                            .handler(ctx -> handle(ctx, sub))
            );
        }
    }

    private void handle(@NotNull CommandContext<Sender> ctx, String sub) {
        Sender sender = ctx.sender();

        if (!VoidAPI.INSTANCE.getConfigManager().isThresholdOptimizerEnabled()) {
            sender.sendMessage(msg(PREFIX + " &c✖ &7The threshold optimizer is disabled."
                    + " Set &fthreshold-optimizer.enabled: true &7in config.yml and run &f/void reload&7."));
            return;
        }

        ThresholdOptimizerManager opt = VoidAPI.INSTANCE.getThresholdOptimizer();
        switch (sub) {
            case "start"   -> handleStart(sender, opt);
            case "stop"    -> handleStop(sender, opt);
            case "save"    -> handleSave(sender, opt);
            case "status"  -> handleStatus(sender, opt);
            case "discard" -> handleDiscard(sender, opt);
        }
    }

    private void handleStart(Sender sender, ThresholdOptimizerManager opt) {
        if (!opt.start()) {
            sender.sendMessage(msg(PREFIX + " &e⚠ &7Optimizer is already running."));
            return;
        }
        sender.sendMessage(msg(PREFIX + " &a✔ &7Optimizer &astarted&7."));
        sender.sendMessage(msg("  &8▸ &7Grant &fvoid.optimizer.legit &7to trusted/legit players so their flags are treated as false positives."));
        sender.sendMessage(msg("  &8▸ &7Let the server run for at least &f24h &7with active play."));
        sender.sendMessage(msg("  &8▸ &e⚠ &7Per-flag analysis is now active, expect a small CPU overhead until you run &f/void optimizer stop&7."));
    }

    private void handleStop(Sender sender, ThresholdOptimizerManager opt) {
        if (!opt.stop()) {
            sender.sendMessage(msg(PREFIX + " &e⚠ &7Optimizer is not running."));
            return;
        }
        sender.sendMessage(msg(PREFIX + " &a✔ &7Optimizer &cstopped&7. Run &f/void optimizer save &7to persist recommendations or &f/void optimizer discard &7to drop them."));
    }

    private void handleSave(Sender sender, ThresholdOptimizerManager opt) {
        if (opt.isRunning()) {
            sender.sendMessage(msg(PREFIX + " &e⚠ &7Stop the optimizer with &f/void optimizer stop &7before saving."));
            return;
        }
        ThresholdOptimizerManager.SaveResult result = opt.save(VoidAPI.INSTANCE.getVoidPlugin().getDataFolder());
        if (!result.ok()) {
            sender.sendMessage(msg(PREFIX + " &c✖ &7Nothing to save &8(" + result.message() + ")&7."));
            return;
        }
        sender.sendMessage(msg(PREFIX + " &a✔ &7Report saved."));
        sender.sendMessage(msg("  &8▸ &7File    &8» &fthreshold-optimizer-report.yml"));
        sender.sendMessage(msg("  &8▸ &7Entries &8» &f" + result.recommendations() + " &7recommendations"));
        sender.sendMessage(msg("  &8▸ &7Apply the thresholds to &fpunishments.yml &7manually."));
    }

    private void handleStatus(Sender sender, ThresholdOptimizerManager opt) {
        sender.sendMessage(msg(PREFIX + " &5Threshold Optimizer &7─ Status"));
        sender.sendMessage(msg(SEP));
        for (String line : opt.getStatusLines()) {
            sender.sendMessage(msg("  " + line));
        }
        sender.sendMessage(msg(SEP));
    }

    private void handleDiscard(Sender sender, ThresholdOptimizerManager opt) {
        if (opt.isRunning()) {
            sender.sendMessage(msg(PREFIX + " &e⚠ &7Stop the optimizer with &f/void optimizer stop &7before discarding."));
            return;
        }
        opt.discard();
        sender.sendMessage(msg(PREFIX + " &a✔ &7Optimizer data discarded."));
    }

    private static Component msg(String legacy) {
        return MessageUtil.miniMessage(legacy);
    }
}
