package ac.voidac.platform.bukkit.scheduler.bukkit;

import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.platform.api.entity.VoidEntity;
import ac.voidac.platform.api.scheduler.EntityScheduler;
import ac.voidac.platform.api.scheduler.TaskHandle;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BukkitEntityScheduler implements EntityScheduler {
    private final BukkitScheduler scheduler = Bukkit.getScheduler();

    @Override
    public void execute(@NotNull VoidEntity entity, @NotNull VoidPlugin plugin, @NotNull Runnable run, @Nullable Runnable retired, long delay) {
        scheduler.runTaskLater(VoidBukkitLoaderPlugin.LOADER, run, delay);
    }

    @Override
    public TaskHandle run(@NotNull VoidEntity entity, @NotNull VoidPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired) {
        return new BukkitTaskHandle(scheduler.runTask(VoidBukkitLoaderPlugin.LOADER, task));
    }

    @Override
    public TaskHandle runDelayed(@NotNull VoidEntity entity, @NotNull VoidPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        return new BukkitTaskHandle(scheduler.runTaskLater(VoidBukkitLoaderPlugin.LOADER, task, delayTicks));
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull VoidEntity entity, @NotNull VoidPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long initialDelayTicks, long periodTicks) {
        return new BukkitTaskHandle(scheduler.runTaskTimer(VoidBukkitLoaderPlugin.LOADER, task, initialDelayTicks, periodTicks));
    }
}
