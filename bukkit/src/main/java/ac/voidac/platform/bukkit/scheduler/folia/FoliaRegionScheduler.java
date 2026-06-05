package ac.voidac.platform.bukkit.scheduler.folia;

import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.platform.api.scheduler.RegionScheduler;
import ac.voidac.platform.api.scheduler.TaskHandle;
import ac.voidac.platform.api.world.PlatformWorld;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import ac.voidac.platform.bukkit.world.BukkitPlatformWorld;
import ac.voidac.utils.math.Location;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public class FoliaRegionScheduler implements RegionScheduler {

    private final io.papermc.paper.threadedregions.scheduler.RegionScheduler regionScheduler = Bukkit.getRegionScheduler();

    @Override
    public void execute(@NotNull VoidPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task) {
        regionScheduler.execute(VoidBukkitLoaderPlugin.LOADER, ((BukkitPlatformWorld) world).bukkitWorld(), chunkX, chunkZ, task);
    }

    @Override
    public void execute(@NotNull VoidPlugin plugin, @NotNull Location location, @NotNull Runnable task) {
        execute(plugin, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
    }

    @Override
    public TaskHandle run(@NotNull VoidPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task) {
        return new FoliaTaskHandle(regionScheduler.run(
                VoidBukkitLoaderPlugin.LOADER,
                ((BukkitPlatformWorld) world).bukkitWorld(),
                chunkX,
                chunkZ,
                ignored -> task.run()
        ));
    }

    @Override
    public TaskHandle run(@NotNull VoidPlugin plugin, @NotNull Location location, @NotNull Runnable task) {
        return run(plugin, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
    }

    @Override
    public TaskHandle runDelayed(@NotNull VoidPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task, long delayTicks) {
        return new FoliaTaskHandle(regionScheduler.runDelayed(
                VoidBukkitLoaderPlugin.LOADER,
                ((BukkitPlatformWorld) world).bukkitWorld(),
                chunkX,
                chunkZ,
                ignored -> task.run(),
                delayTicks
        ));
    }

    @Override
    public TaskHandle runDelayed(@NotNull VoidPlugin plugin, @NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return runDelayed(plugin, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task, delayTicks);
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull VoidPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return new FoliaTaskHandle(regionScheduler.runAtFixedRate(
                VoidBukkitLoaderPlugin.LOADER,
                ((BukkitPlatformWorld) world).bukkitWorld(),
                chunkX,
                chunkZ,
                ignored -> task.run(),
                initialDelayTicks,
                periodTicks
        ));
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull VoidPlugin plugin, @NotNull Location location, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return runAtFixedRate(
                plugin,
                location.getWorld(),
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4,
                task,
                initialDelayTicks,
                periodTicks
        );
    }
}
