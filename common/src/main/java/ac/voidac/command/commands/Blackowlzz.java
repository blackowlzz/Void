package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public class Blackowlzz implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                // i mean the plugin its free, a little self promotion never hurt anyone
                commandManager.commandBuilder("blackowlzz", "ac", "anticheat")
                        .permission("void.advert")
                        .handler(this::handleBlackowlzz)
        );
    }

    // also sent from the packet listener, so it lives here and not inline
    public static Component advert() {
        String version = VoidAPI.INSTANCE.getVoidPlugin().getDescription().getVersion();
        return MessageUtil.miniMessage(
            "<newline>" +
            "&7This server uses &5VoidAC &8(&7" + version + "&8)&7, want to use it on yours?<newline>" +
            "<newline>" +
            "  &a  Modrinth &8» &fhttps://modrinth.com/plugin/voidac<newline>" +
            "  &b  Discord  &8» &fhttps://discord.gg/DRnhHrcseU<newline>"
        );
    }

    private void handleBlackowlzz(CommandContext<Sender> context) {
        context.sender().sendMessage(advert());
    }
}
