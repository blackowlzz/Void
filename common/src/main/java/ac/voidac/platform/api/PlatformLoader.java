package ac.voidac.platform.api;

import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.platform.api.command.CommandService;
import ac.voidac.platform.api.manager.ItemResetHandler;
import ac.voidac.platform.api.manager.MessagePlaceHolderManager;
import ac.voidac.platform.api.manager.PermissionRegistrationManager;
import ac.voidac.platform.api.manager.PlatformPluginManager;
import ac.voidac.platform.api.player.PlatformPlayerFactory;
import ac.voidac.platform.api.scheduler.PlatformScheduler;
import ac.voidac.platform.api.sender.SenderFactory;
import com.github.retrooper.packetevents.PacketEventsAPI;
import org.jetbrains.annotations.NotNull;

public interface PlatformLoader {
    PlatformScheduler getScheduler();

    PlatformPlayerFactory getPlatformPlayerFactory();

    PacketEventsAPI<?> getPacketEvents();

    ItemResetHandler getItemResetHandler();

    CommandService getCommandService();

    SenderFactory<?> getSenderFactory();

    VoidPlugin getPlugin();

    PlatformPluginManager getPluginManager();

    PlatformServer getPlatformServer();

    // Intended for use for platform specific service/API bringup
    // Method will be called when InitManager.load() is called
    void registerAPIService();

    // Used to replace text placeholders in messages
    // Currently only supports PlaceHolderAPI on Bukkit
    @NotNull
    MessagePlaceHolderManager getMessagePlaceHolderManager();

    PermissionRegistrationManager getPermissionManager();
}
