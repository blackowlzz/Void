package ac.voidac.platform.bukkit.scheduler.bukkit;

import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.platform.api.scheduler.GlobalRegionScheduler;
import ac.voidac.platform.api.scheduler.TaskHandle;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

public class BukkitGlobalRegionScheduler implements GlobalRegionScheduler {

    private final BukkitScheduler bukkitScheduler = Bukkit.getScheduler();

    @Override
    public void execute(@NotNull VoidPlugin plugin, @NotNull Runnable task) {
        bukkitScheduler.runTask(VoidBukkitLoaderPlugin.LOADER, task);
    }

    @Override
    public TaskHandle run(@NotNull VoidPlugin plugin, @NotNull Runnable task) {
        return new BukkitTaskHandle(bukkitScheduler.runTask(VoidBukkitLoaderPlugin.LOADER, task));
    }

    @Override
    public TaskHandle runDelayed(@NotNull VoidPlugin plugin, @NotNull Runnable task, long delay) {
        return new BukkitTaskHandle(bukkitScheduler.runTaskLater(VoidBukkitLoaderPlugin.LOADER, task, delay));
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull VoidPlugin plugin, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return new BukkitTaskHandle(bukkitScheduler.runTaskTimer(VoidBukkitLoaderPlugin.LOADER, task, initialDelayTicks, periodTicks));
    }

    @Override
    public void cancel(@NotNull VoidPlugin plugin) {
        bukkitScheduler.cancelTasks(VoidBukkitLoaderPlugin.LOADER);
    }
}
