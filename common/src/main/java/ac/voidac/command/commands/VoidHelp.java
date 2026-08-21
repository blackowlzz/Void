package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

public class VoidHelp implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        // /void (no args), show help panel
        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .permission("void.help")
                        .handler(this::handleRoot)
        );
        // /void help
        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .literal("help", Description.of("Open the void help index"))
                        .permission("void.help")
                        .handler(this::handleHelp)
        );
    }

    private void handleRoot(@NotNull CommandContext<Sender> context) {
        sendHelp(context.sender());
    }

    private void handleHelp(@NotNull CommandContext<Sender> context) {
        sendHelp(context.sender());
    }

    private void sendHelp(@NotNull Sender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text()
                .append(Component.text("  VoidAC", NamedTextColor.DARK_PURPLE))
                .append(Component.text("  ─  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Staff Panel", NamedTextColor.GRAY))
                .build());
        sender.sendMessage(MessageUtil.miniMessage("&8&m                                        "));

        for (String string : VoidAPI.INSTANCE.getConfigManager().getConfig().getStringListElse("help", java.util.List.of())) {
            if (string == null) continue;
            string = MessageUtil.replacePlaceholders(sender, string);
            sender.sendMessage(MessageUtil.miniMessage(string));
        }

        sender.sendMessage(MessageUtil.miniMessage("&8&m                                        "));
    }
}
