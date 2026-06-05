package ac.voidac.platform.fabric.scheduler;

import ac.voidac.VoidAPI;
import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.platform.api.scheduler.*;
import ac.voidac.utils.anticheat.LogUtil;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FabricPlatformScheduler implements PlatformScheduler {
    private final FabricAsyncScheduler asyncScheduler;
    private final FabricGlobalRegionScheduler globalRegionScheduler;
    private final FabricEntityScheduler entityScheduler;
    private final FabricRegionScheduler regionScheduler;

    public FabricPlatformScheduler() {
        VoidPlugin plugin = VoidAPI.INSTANCE.getVoidPlugin();
        this.asyncScheduler = new FabricAsyncScheduler(plugin);
        this.globalRegionScheduler = new FabricGlobalRegionScheduler(plugin);
        this.entityScheduler = new FabricEntityScheduler(plugin);
        this.regionScheduler = new FabricRegionScheduler(plugin);
    }

    // Shared method to handle synchronous tasks
    // Add this to FabricPlatformScheduler.java
    public static final ThreadLocal<Boolean> EXECUTING_TASK = ThreadLocal.withInitial(() -> false);

    protected static void handleSyncTasks(Map<ScheduledTask, Runnable> taskMap, MinecraftServer server, VoidPlugin plugin) {
        Iterator<ScheduledTask> iterator = taskMap.keySet().iterator();
        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();
            if (server.getTickCount() >= task.nextRunTick) {
                try {
                    EXECUTING_TASK.set(true);
                    task.task.run();
                } catch (Exception e) {
                    LogUtil.error("Error executing scheduled task ", e);
                } finally {
                    EXECUTING_TASK.set(false);
                }

                if (task.isPeriodic) {
                    task.nextRunTick = server.getTickCount() + task.period;
                } else {
                    iterator.remove();
                }
            }
        }
    }

    // Cancel tasks for a specific plugin
    protected static void cancelPluginTasks(Map<ScheduledTask, Runnable> taskMap, VoidPlugin plugin) {
        Iterator<Map.Entry<ScheduledTask, Runnable>> iterator = taskMap.entrySet().iterator();
        List<Runnable> cancellationTasks = new ArrayList<>();

        while (iterator.hasNext()) {
            Map.Entry<ScheduledTask, Runnable> entry = iterator.next();
            if (entry.getKey().plugin.equals(plugin)) {
                cancellationTasks.add(entry.getValue());
                iterator.remove();
            }
        }

        for (Runnable cancellationTask : cancellationTasks) {
            cancellationTask.run();
        }
    }

    // Cancel all tasks (renamed from cancelAllTasks)
    protected static void cancelAllTasks(Map<?, Runnable> taskMap) {
        List<Runnable> cancellationTasks = new ArrayList<>(taskMap.values());
        taskMap.clear();
        for (Runnable cancellationTask : cancellationTasks) {
            cancellationTask.run();
        }
    }

    protected static void scheduleTask(Map<ScheduledTask, Runnable> taskMap, VoidPlugin plugin, Runnable task, long initialDelayTicks, long periodTicks, boolean isPeriodic) {

    }

    @Override
    public @NotNull AsyncScheduler getAsyncScheduler() {
        return asyncScheduler;
    }

    @Override
    public @NotNull GlobalRegionScheduler getGlobalRegionScheduler() {
        return globalRegionScheduler;
    }

    @Override
    public @NotNull EntityScheduler getEntityScheduler() {
        return entityScheduler;
    }

    @Override
    public @NotNull RegionScheduler getRegionScheduler() {
        return regionScheduler;
    }

    /**
     * Shuts down all schedulers and cancels all pending tasks.
     * This method should be called when the server is shutting down.
     */
    public void shutdown() {
        asyncScheduler.cancelAll();
        globalRegionScheduler.cancelAll();
        entityScheduler.cancelAll();
        regionScheduler.cancelAll();
    }

    protected static class ScheduledTask {
        final Runnable task;
        final long period;
        final boolean isPeriodic;
        final VoidPlugin plugin; // Add plugin reference
        long nextRunTick;

        ScheduledTask(Runnable task, long nextRunTick, long period, boolean isPeriodic, VoidPlugin plugin) {
            this.task = task;
            this.nextRunTick = nextRunTick;
            this.period = period;
            this.isPeriodic = isPeriodic;
            this.plugin = plugin;
        }
    }
}
