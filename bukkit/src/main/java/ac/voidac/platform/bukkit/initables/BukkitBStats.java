package ac.voidac.platform.bukkit.initables;

import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import ac.voidac.utils.anticheat.Constants;
import org.bstats.bukkit.Metrics;

public class BukkitBStats implements StartableInitable {
    @Override
    public void start() {
        try {
            new Metrics(VoidBukkitLoaderPlugin.LOADER, Constants.BSTATS_PLUGIN_ID);
        } catch (Exception ignored) {}
    }
}
