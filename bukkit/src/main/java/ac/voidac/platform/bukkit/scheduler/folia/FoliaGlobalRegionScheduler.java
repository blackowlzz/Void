package ac.voidac.platform.bukkit.scheduler.folia;

import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.platform.api.scheduler.GlobalRegionScheduler;
import ac.voidac.platform.api.scheduler.TaskHandle;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public class FoliaGlobalRegionScheduler implements GlobalRegionScheduler {

    private final io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler globalRegionScheduler = Bukkit.getGlobalRegionScheduler();

    @Override
    public void execute(@NotNull VoidPlugin plugin, @NotNull Runnable task) {
        globalRegionScheduler.execute(VoidBukkitLoaderPlugin.LOADER, task);
    }

    @Override
    public TaskHandle run(@NotNull VoidPlugin plugin, @NotNull Runnable task) {
        return new FoliaTaskHandle(globalRegionScheduler.run(VoidBukkitLoaderPlugin.LOADER, ignored -> task.run()));
    }

    @Override
    public TaskHandle runDelayed(@NotNull VoidPlugin plugin, @NotNull Runnable task, long delay) {
        return new FoliaTaskHandle(globalRegionScheduler.runDelayed(VoidBukkitLoaderPlugin.LOADER, ignored -> task.run(), delay));
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull VoidPlugin plugin, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return new FoliaTaskHandle(globalRegionScheduler.runAtFixedRate(VoidBukkitLoaderPlugin.LOADER, ignored -> task.run(), initialDelayTicks, periodTicks));
    }

    @Override
    public void cancel(@NotNull VoidPlugin plugin) {
        globalRegionScheduler.cancelTasks(VoidBukkitLoaderPlugin.LOADER);
    }
}
