package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.command.PunishmentCommandResolver;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

import static ac.voidac.command.render.MessageStyle.*;

public class VoidUnban implements BuildableCommand {
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
                    String externalCommand = PunishmentCommandResolver.resolveUnbanCommand(
                            VoidAPI.INSTANCE.getConfigManager().getUnbanCommand(), name).orElse(null);
                    if (externalCommand != null) {
                        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                                VoidAPI.INSTANCE.getVoidPlugin(),
                                () -> {
                                    VoidAPI.INSTANCE.getPlatformServer().dispatchCommand(
                                            VoidAPI.INSTANCE.getPlatformServer().getConsoleSender(), externalCommand);
                                    sender.sendMessage(success(sc("Unban processed for ") + "&f" + name));
                                }
                        );
                    } else if (removed == null) {
                        sender.sendMessage(error("&f" + name + " " + sc("has no active Void ban")));
                    } else {
                        sender.sendMessage(success("&f" + name + " " + sc("has been unbanned")));
                    }
                }
        );
    }
}
