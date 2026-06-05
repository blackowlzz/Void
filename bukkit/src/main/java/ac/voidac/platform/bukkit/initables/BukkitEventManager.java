package ac.voidac.platform.bukkit.initables;

import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import ac.voidac.platform.bukkit.events.BukkitLoginListener;
import ac.voidac.platform.bukkit.events.PistonEvent;
import ac.voidac.utils.anticheat.LogUtil;
import org.bukkit.Bukkit;

public class BukkitEventManager implements StartableInitable {
    public void start() {
        LogUtil.info("Registering singular bukkit events... (PistonEvent, BukkitLoginListener)");

        Bukkit.getPluginManager().registerEvents(new PistonEvent(), VoidBukkitLoaderPlugin.LOADER);
        Bukkit.getPluginManager().registerEvents(new BukkitLoginListener(), VoidBukkitLoaderPlugin.LOADER);
    }
}
