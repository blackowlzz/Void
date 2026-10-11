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
                commandManager.commandBuilder("blackowlzz", "ac", "anticheat")
                        .permission("void.advert")
                        .handler(this::handleBlackowlzz)
        );
    }

    public static Component advert() {
        String version = VoidAPI.INSTANCE.getVoidPlugin().getDescription().getVersion();
        return MessageUtil.miniMessage(
            "<newline>" +
            "%prefix% &7<sc>This server uses</sc> &5VoidAC &8(&7" + version + "&8)&7<sc>, want it on yours?</sc><newline>" +
            "<newline>" +
            "  &8▸ &a<sc>Modrinth</sc> &8│ <click:open_url:'https://modrinth.com/plugin/voidac'>&fhttps://modrinth.com/plugin/voidac</click><newline>" +
            "  &8▸ &b<sc>Discord</sc> &8│ <click:open_url:'https://discord.gg/DRnhHrcseU'>&fhttps://discord.gg/DRnhHrcseU</click><newline>"
        );
    }

    private void handleBlackowlzz(CommandContext<Sender> context) {
        context.sender().sendMessage(advert());
    }
}
