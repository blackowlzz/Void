package ac.voidac.manager.init.start;

import ac.voidac.VoidAPI;
import ac.voidac.platform.api.Platform;
import ac.voidac.utils.anticheat.LogUtil;

public class TickRunner implements StartableInitable {
    @Override
    public void start() {
        LogUtil.info("Registering tick schedulers...");

        final VoidAPI api = VoidAPI.INSTANCE;

        if (api.getPlatform() == Platform.FOLIA) {
            final var tickManager = api.getTickManager();
            api.getScheduler().getAsyncScheduler().runAtFixedRate(api.getVoidPlugin(), () -> {
                tickManager.tickSync();
                tickManager.tickAsync();
            }, 1, 1);
        } else {
            final var scheduler = api.getScheduler();
            final var tickManager = api.getTickManager();
            final var plugin = api.getVoidPlugin();

            scheduler.getGlobalRegionScheduler().runAtFixedRate(plugin, tickManager::tickSync, 0, 1);
            scheduler.getAsyncScheduler().runAtFixedRate(plugin, tickManager::tickAsync, 0, 1);
        }
    }
}
