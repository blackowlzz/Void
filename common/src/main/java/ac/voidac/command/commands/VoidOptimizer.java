package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.manager.ThresholdOptimizerManager;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import static ac.voidac.command.render.MessageStyle.*;

public class VoidOptimizer implements BuildableCommand {
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
            sender.sendMessage(error(sc("The threshold optimizer is disabled. Set ")
                    + "&fthreshold-optimizer.enabled: true &7" + sc("in config.yml and run ") + "&f/void reload&7."));
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
            sender.sendMessage(warning(sc("Optimizer is already running")));
            return;
        }
        sender.sendMessage(success(sc("Optimizer ") + "&a" + sc("started")));
        sender.sendMessage(bullet(sc("Grant ") + "&fvoid.optimizer.legit &7" + sc("to trusted players so their flags count as false positives")));
        sender.sendMessage(bullet(sc("Let the server run for at least ") + "&f24h &7" + sc("with active play")));
        sender.sendMessage(bullet("&e⚠ &7" + sc("Per-flag analysis adds a small CPU overhead until you run ") + "&f/void optimizer stop"));
    }

    private void handleStop(Sender sender, ThresholdOptimizerManager opt) {
        if (!opt.stop()) {
            sender.sendMessage(warning(sc("Optimizer is not running")));
            return;
        }
        sender.sendMessage(success(sc("Optimizer ") + "&c" + sc("stopped")));
        sender.sendMessage(bullet("&f/void optimizer save &7" + sc("keeps the recommendations")));
        sender.sendMessage(bullet("&f/void optimizer discard &7" + sc("drops them")));
    }

    private void handleSave(Sender sender, ThresholdOptimizerManager opt) {
        if (opt.isRunning()) {
            sender.sendMessage(warning(sc("Stop the optimizer with ") + "&f/void optimizer stop &7" + sc("before saving")));
            return;
        }
        ThresholdOptimizerManager.SaveResult result = opt.save(VoidAPI.INSTANCE.getVoidPlugin().getDataFolder());
        if (!result.ok()) {
            sender.sendMessage(error(sc("Nothing to save ") + "&8(&7" + result.message() + "&8)"));
            return;
        }
        sender.sendMessage(success(sc("Report saved")));
        sender.sendMessage(detail("File", "threshold-optimizer-report.yml"));
        sender.sendMessage(detail("Entries", result.recommendations() + " &7" + sc("recommendations")));
        sender.sendMessage(bullet(sc("Apply the thresholds to ") + "&fpunishments.yml &7" + sc("manually")));
    }

    private void handleStatus(Sender sender, ThresholdOptimizerManager opt) {
        sender.sendMessage(header("Threshold Optimizer", sc("Status")));
        sender.sendMessage(separator());
        for (String line : opt.getStatusLines()) {
            sender.sendMessage(MessageUtil.miniMessage("  " + line));
        }
        sender.sendMessage(separator());
    }

    private void handleDiscard(Sender sender, ThresholdOptimizerManager opt) {
        if (opt.isRunning()) {
            sender.sendMessage(warning(sc("Stop the optimizer with ") + "&f/void optimizer stop &7" + sc("before discarding")));
            return;
        }
        opt.discard();
        sender.sendMessage(success(sc("Optimizer data discarded")));
    }
}
