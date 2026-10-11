package ac.voidac.platform.bukkit.initables;

import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import ac.voidac.platform.bukkit.compat.CoordinateOffsetPipelineGuard;
import ac.voidac.platform.bukkit.events.BukkitLoginListener;
import ac.voidac.platform.bukkit.events.PistonEvent;
import ac.voidac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;

public class BukkitEventManager implements StartableInitable {
    public void start() {
        LogUtil.info("Registering singular bukkit events... (PistonEvent, BukkitLoginListener)");

        Bukkit.getPluginManager().registerEvents(new PistonEvent(), VoidBukkitLoaderPlugin.LOADER);
        Bukkit.getPluginManager().registerEvents(new BukkitLoginListener(), VoidBukkitLoaderPlugin.LOADER);
        PacketEvents.getAPI().getEventManager().registerListener(new CoordinateOffsetPipelineGuard());
    }
}
