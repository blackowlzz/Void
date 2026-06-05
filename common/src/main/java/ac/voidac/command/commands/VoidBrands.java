package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.manager.AlertManagerImpl;
import ac.voidac.manager.datastore.PlayerToggleStore;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.platform.api.sender.Sender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class VoidBrands implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("void", "voidac", "void", "voidac")
                        .literal("brands", Description.of("Reveal or hide client brands"))
                        .permission("void.brand")
                        .handler(this::handleBrands)
        );
    }

    private void handleBrands(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        if (sender.isPlayer()) {
            PlatformPlayer p = Objects.requireNonNull(context.sender().getPlatformPlayer());
            AlertManagerImpl am = VoidAPI.INSTANCE.getAlertManager();
            boolean newState = !am.hasBrandsEnabled(p);
            am.setBrandsEnabled(p, newState, false);
            VoidAPI.INSTANCE.getDataStoreLifecycle().playerToggleStore()
                    .applyUserToggle(p.getUniqueId(), PlayerToggleStore.KEY_BRANDS, newState);
        } else if (sender.isConsole()) {
            VoidAPI.INSTANCE.getAlertManager().toggleConsoleBrands();
        }
    }
}
