package ac.voidac.command.commands;

import ac.voidac.command.BuildableCommand;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public class Blackowlzz implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("blackowlzz")
                        .handler(this::handleBlackowlzz)
        );
    }

    private void handleBlackowlzz(CommandContext<Sender> context) {
        context.sender().sendMessage(MessageUtil.miniMessage("&7the void is watching"));
    }
}
