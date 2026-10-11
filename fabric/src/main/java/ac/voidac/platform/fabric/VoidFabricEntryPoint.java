package ac.voidac.platform.fabric;

import ac.voidac.VoidAPI;
import ac.voidac.platform.fabric.initables.FabricBStats;
import ac.voidac.platform.fabric.initables.FabricTickEndEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.util.List;

public class VoidFabricEntryPoint implements PreLaunchEntrypoint, ModInitializer {
    @Override
    public void onPreLaunch() {
    }

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        String chainLoadEntryPointName = "voidMainLoad";

        List<VoidFabricLoaderPlugin> mainChainLoadEntryPoints = loader.getEntrypoints(chainLoadEntryPointName, VoidFabricLoaderPlugin.class);
        mainChainLoadEntryPoints.sort((a, b) -> b.getNativeVersion().getProtocolVersion() - a.getNativeVersion().getProtocolVersion());

        VoidFabricLoaderPlugin platformLoader = mainChainLoadEntryPoints.get(0);
        VoidFabricLoaderPlugin.LOADER = platformLoader;

        VoidAPI.INSTANCE.load(
                platformLoader,
                new FabricBStats(),
                new FabricTickEndEvent()
        );

        VoidAPI.INSTANCE.getCommandService().registerCommands();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            VoidFabricLoaderPlugin.FABRIC_SERVER = server;
            VoidAPI.INSTANCE.start();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
            VoidAPI.INSTANCE.getPlayerDataManager().getEntries().forEach(player -> {
                try {
                    player.storageEspDecoyManager.restoreAllDecoys();
                    player.storageEspDecoyManager.shutdown();
                } catch (Exception ignored) {
                }
            });
            if (VoidAPI.INSTANCE.getInitManager() != null
                    && !VoidAPI.INSTANCE.getInitManager().isStopped()) {
                VoidAPI.INSTANCE.stop();
            }
            platformLoader.getScheduler().shutdown();
        });
    }
}
