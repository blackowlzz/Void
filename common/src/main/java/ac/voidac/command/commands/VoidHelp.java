package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static ac.voidac.command.render.MessageStyle.*;

public class VoidHelp implements BuildableCommand {
    private static final int PAGE_SIZE = 7;

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .permission("void.help")
                        .handler(context -> sendHelp(context.sender(), 1))
        );
        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .literal("help", Description.of("Open the void help index"))
                        .permission("void.help")
                        .optional("page", IntegerParser.integerParser(1))
                        .handler(this::handleHelp)
        );
    }

    private void handleHelp(@NotNull CommandContext<Sender> context) {
        sendHelp(context.sender(), context.getOrDefault("page", 1));
    }

    private void sendHelp(@NotNull Sender sender, int requestedPage) {
        List<String> lines = VoidAPI.INSTANCE.getConfigManager().getConfig().getStringListElse("help", List.of());
        int pages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(Math.max(requestedPage, 1), pages);

        sender.sendMessage(MessageUtil.miniMessage("  &5VoidAC &8· &7" + sc("Staff Panel")
                + " &8v" + VoidAPI.INSTANCE.getExternalAPI().getVoidVersion()));

        for (String line : lines.subList((page - 1) * PAGE_SIZE, Math.min(lines.size(), page * PAGE_SIZE))) {
            if (line == null) continue;
            sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, line)));
        }

        if (pages > 1) {
            sender.sendMessage(MessageUtil.miniMessage("  " + arrow("◂", page > 1, page - 1)
                    + " &7" + page + "&8/&7" + pages + " " + arrow("▸", page < pages, page + 1)));
        }
    }

    private static String arrow(String symbol, boolean active, int target) {
        return active
                ? "<click:run_command:'/void help " + target + "'>&5" + symbol + "</click>"
                : "&8" + symbol;
    }
}
