package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.api.VoidIdentity;
import ac.voidac.command.BuildableCommand;
import ac.voidac.command.CommandUtils;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ac.voidac.command.render.MessageStyle.*;

public class VoidList implements BuildableCommand {

    // Mainly for debugging purposes. Useful for seeing which players are exempt or not.

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(commandManager.commandBuilder("void", "voidac", "void", "voidac")
                .literal("list")
                .permission("void.list")
                .required("list", StringParser.stringParser(), SUGGESTIONS)
                .handler(commandContext -> handleList(commandContext.sender(), commandContext.getOrDefault("list", "?").toLowerCase()))
                .build());
    }

    private final SuggestionProvider<Sender> SUGGESTIONS = CommandUtils.fromStrings("players");

    private void handleList(Sender sender, String id) {
        if ("players".equals(id)) {
            handleListPlayers(sender);
            return;
        }

        sender.sendMessage(error(sc("Unrecognized argument: ") + "&f" + id));
    }

    private Component playerComponent(String name, UUID uuid, boolean online, boolean exempt) {
        return Component.text(name)
                .color(exempt ? (online ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY)
                        : (online ? NamedTextColor.WHITE : NamedTextColor.RED))
                .clickEvent(ClickEvent.copyToClipboard(name))
                .hoverEvent(HoverEvent.showText(playerHoverComponent(uuid, online, exempt, true)));
    }

    private Component playerHoverComponent(UUID uuid, boolean online, boolean exempt, boolean registered) {
        return MessageUtil.miniMessage("&7" + sc("UUID") + "&8: &f" + uuid
                + "<newline>&7" + sc("State") + "&8: " + (online ? "&a" + sc("Online") : "&c" + sc("Offline"))
                + (exempt ? "<newline>&d" + sc("Void-exempt") : "")
                + (registered ? "" : "<newline>&c" + sc("Not registered")));
    }

    private void handleListPlayers(Sender sender) {
        final List<Component> entries = new ArrayList<>();

        final Map<UUID, PlatformPlayer> onlinePlayers = VoidAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers()
                .stream().collect(Collectors.toMap(VoidIdentity::getUniqueId, Function.identity()));
        final Set<PlatformPlayer> unregisteredPlayers = new HashSet<>(onlinePlayers.values());

        for (VoidPlayer entry : VoidAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            PlatformPlayer platformPlayer = onlinePlayers.get(entry.getUniqueId());
            if (platformPlayer != null) unregisteredPlayers.remove(platformPlayer);
            boolean online = platformPlayer != null && platformPlayer.isOnline();
            boolean exempt = !VoidAPI.INSTANCE.getPlayerDataManager().shouldCheck(entry.user);
            entries.add(playerComponent(entry.getName(), entry.getUniqueId(), online, exempt));
        }

        for (PlatformPlayer platformPlayer : unregisteredPlayers) {
            entries.add(Component.text(platformPlayer.getName()).color(NamedTextColor.LIGHT_PURPLE)
                    .clickEvent(ClickEvent.suggestCommand(platformPlayer.getName()))
                    .hoverEvent(HoverEvent.showText(playerHoverComponent(platformPlayer.getUniqueId(), platformPlayer.isOnline(), false, false)))
            );
        }

        sender.sendMessage(header("Players", "&8(&f" + entries.size() + "&8)"));
        sender.sendMessage(Component.text()
                .append(Component.text("  "))
                .append(Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.DARK_GRAY)), entries))
                .build());
    }

}
