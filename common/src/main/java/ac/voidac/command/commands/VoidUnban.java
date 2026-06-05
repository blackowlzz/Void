package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * /void unban <player>
 *
 * Removes a player from Void's native ban database (void_active_bans).
 * Does not interact with LiteBans or other ban plugins.
 *
 * Permission: void.unban
 */
public class VoidUnban implements BuildableCommand {

    private static final String PREFIX = "&8[&5Void&8]";

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .literal("unban")
                        .permission("void.unban")
                        .required("name", StringParser.stringParser())
                        .handler(this::handleUnban)
        );
    }

    private void handleUnban(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        String name = context.<String>get("name").trim();

        VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> {
                    UUID removed = VoidAPI.INSTANCE.getVoidBanManager().unbanByName(name);
                    if (removed == null) {
                        sender.sendMessage(msg(PREFIX + " &c✖ &f" + name
                                + " &7has no active Void native ban."));
                    } else {
                        sender.sendMessage(msg(PREFIX + " &a✔ &f" + name
                                + " &7has been unbanned from Void."));
                    }
                }
        );
    }

    private static Component msg(String legacy) {
        return MessageUtil.miniMessage(legacy);
    }
}
