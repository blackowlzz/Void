package ac.voidac.platform.bukkit.scheduler.folia;

import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.platform.api.entity.VoidEntity;
import ac.voidac.platform.api.scheduler.EntityScheduler;
import ac.voidac.platform.api.scheduler.TaskHandle;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import ac.voidac.platform.bukkit.entity.BukkitVoidEntity;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FoliaEntityScheduler implements EntityScheduler {

    @Override
    public void execute(@NotNull VoidEntity entity, @NotNull VoidPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long delay) {
        ((BukkitVoidEntity) entity).getBukkitEntity().getScheduler().execute(VoidBukkitLoaderPlugin.LOADER, task, retired, delay);
    }

    @Override
    public TaskHandle run(@NotNull VoidEntity entity, @NotNull VoidPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired) {
        ScheduledTask scheduled = ((BukkitVoidEntity) entity).getBukkitEntity().getScheduler().run(
                VoidBukkitLoaderPlugin.LOADER,
                ignored -> task.run(),
                retired
        );

        return scheduled == null ? null : new FoliaTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runDelayed(@NotNull VoidEntity entity, @NotNull VoidPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        ScheduledTask scheduled = ((BukkitVoidEntity) entity).getBukkitEntity().getScheduler().runDelayed(
                VoidBukkitLoaderPlugin.LOADER,
                ignored -> task.run(),
                retired,
                delayTicks
        );

        return scheduled == null ? null : new FoliaTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull VoidEntity entity, @NotNull VoidPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long initialDelayTicks, long periodTicks) {
        ScheduledTask scheduled = ((BukkitVoidEntity) entity).getBukkitEntity().getScheduler().runAtFixedRate(
                VoidBukkitLoaderPlugin.LOADER,
                ignored -> task.run(),
                retired,
                initialDelayTicks,
                periodTicks
        );

        return scheduled == null ? null : new FoliaTaskHandle(scheduled);
    }
}
