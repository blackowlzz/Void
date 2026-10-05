package ac.voidac.command.commands;

import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.predictionengine.MovementCheckRunner;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import static ac.voidac.command.render.MessageStyle.*;

public class VoidPerf {

    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        Command.Builder<Sender> voidCommand = commandManager.commandBuilder("void", "voidac", "void", "voidac");

        Command.Builder<Sender> configuredBuilder = voidCommand
                .literal("perf", "performance")
                .permission("void.performance")
                .handler(this::handlePerformance);

        commandManager.command(configuredBuilder);
    }

    private void handlePerformance(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();

        double millis = MovementCheckRunner.predictionNanos / 1000000;
        double longMillis = MovementCheckRunner.longPredictionNanos / 1000000;

        sender.sendMessage(header("Performance", sc("Prediction time")));
        sender.sendMessage(separator());
        sender.sendMessage(detail("Average of 500", millis + " ms"));
        sender.sendMessage(detail("Average of 20k", longMillis + " ms"));
        sender.sendMessage(separator());
    }
}
