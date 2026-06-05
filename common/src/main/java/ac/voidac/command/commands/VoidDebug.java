package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.platform.api.command.PlayerSelector;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.MessageUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VoidDebug implements BuildableCommand {

    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        Command.Builder<Sender> voidCommand = commandManager.commandBuilder("void", "voidac", "void", "voidac");

        // Register "debug" subcommand
        Command.Builder<Sender> debugCommand = voidCommand
                .literal("debug", Description.of("Toggle a player's void trace output"))
                .permission("void.debug")
                .optional("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleDebug);

        // Register "consoledebug" subcommand
        Command.Builder<Sender> consoleDebugCommand = voidCommand
                .literal("consoledebug", Description.of("Toggle console echo for a player"))
                .permission("void.consoledebug")
                .required("target", adapter.singlePlayerSelectorParser())
                .handler(this::handleConsoleDebug);

        // Register command
        commandManager.command(debugCommand);
        commandManager.command(consoleDebugCommand);
    }

    private void handleDebug(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector playerSelector = context.getOrDefault("target", null);

        VoidPlayer targetVoidPlayer = parseTarget(sender, playerSelector == null ? sender : playerSelector.getSinglePlayer());
        if (targetVoidPlayer == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-found", "%prefix% &cThat player is exempt or offline."));
            return;
        }

        if (sender.isConsole()) {
            targetVoidPlayer.checkManager.getDebugHandler().toggleConsoleOutput();
        } else if (sender.isPlayer()) {
            VoidPlayer senderVoidPlayer = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(sender.getUniqueId());
            if (senderVoidPlayer == null) {
                sender.sendMessage(MessageUtil.getParsedComponent(sender, "sender-not-found", "%prefix% &cExempt users cannot use this command."));
                return;
            }
            targetVoidPlayer.checkManager.getDebugHandler().toggleListener(senderVoidPlayer);
        } else {
            sender.sendMessage(MessageUtil.getParsedComponent(sender,
                    "run-as-player-or-console",
                    "%prefix% &cOnly players or the console can use this command.")
            );
        }
    }

    private void handleConsoleDebug(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector targetName = context.getOrDefault("target", null);

        VoidPlayer voidPlayer = parseTarget(sender, targetName.getSinglePlayer());
        if (voidPlayer == null) return;

        boolean isOutput = voidPlayer.checkManager.getDebugHandler().toggleConsoleOutput();
        String playerName = voidPlayer.user.getProfile().getName(); // Use user profile for name

        Component message = Component.text()
                .append(Component.text("Console echo for ", NamedTextColor.GRAY))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(" is now ", NamedTextColor.GRAY))
                .append(Component.text(isOutput ? "enabled" : "disabled", NamedTextColor.WHITE))
                .build();

        sender.sendMessage(message);
    }

    private @Nullable VoidPlayer parseTarget(@NotNull Sender sender, @Nullable Sender t) {
        if (sender.isConsole() && t == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "console-specify-target", "%prefix% &cYou must specify a target as the console!"));
            return null;
        }
        Sender target = t == null ? sender : t;

        VoidPlayer voidPlayer = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(target.getUniqueId());
        if (voidPlayer == null) {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(sender.getPlatformPlayer().getNative());
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-found", "%prefix% &cThat player is exempt or offline."));

            if (user == null) {
                sender.sendMessage(Component.text("PacketEvents user could not be resolved", NamedTextColor.RED));
            } else {
                boolean isExempt = VoidAPI.INSTANCE.getPlayerDataManager().shouldCheck(user);
                if (!isExempt) {
                    sender.sendMessage(Component.text("User state: " + user.getConnectionState(), NamedTextColor.RED));
                }
            }
        }

        return voidPlayer;
    }
}
