package ac.voidac.platform.fabric;

import ac.voidac.VoidAPI;
import ac.voidac.api.VoidAPIProvider;
import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.command.CloudCommandService;
import ac.voidac.internal.plugin.resolver.VoidExtensionManager;
import ac.voidac.platform.api.PlatformLoader;
import ac.voidac.platform.api.command.CommandService;
import ac.voidac.platform.api.manager.*;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.platform.api.sender.SenderFactory;
import ac.voidac.platform.fabric.manager.*;
import ac.voidac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.voidac.platform.fabric.resolver.FabricResolverRegistrar;
import ac.voidac.platform.fabric.scheduler.FabricPlatformScheduler;
import ac.voidac.platform.fabric.sender.FabricSenderFactory;
import ac.voidac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.voidac.platform.fabric.utils.message.IFabricMessageUtil;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import lombok.Getter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.fabric.FabricServerCommandManager;
import org.jetbrains.annotations.NotNull;

public abstract class VoidFabricLoaderPlugin implements PlatformLoader {
    public static MinecraftServer FABRIC_SERVER;
    public static VoidFabricLoaderPlugin LOADER;

    protected final LazyHolder<FabricPlatformScheduler> scheduler = LazyHolder.simple(FabricPlatformScheduler::new);
    // Since we JiJ PacketEvents and depend on it on Fabric, we can always just get the API instance since it loads firsts
    protected final PacketEventsAPI<?> packetEvents = PacketEvents.getAPI();
    protected final LazyHolder<FabricSenderFactory> senderFactory = LazyHolder.simple(FabricSenderFactory::new);
    protected final LazyHolder<ItemResetHandler> itemResetHandler = LazyHolder.simple(FabricItemResetHandler::new);
    protected final LazyHolder<CommandService> commandService = LazyHolder.simple(this::createCommandService);
    protected final VoidPlugin plugin;
    @Getter
    protected final PlatformPluginManager pluginManager = new FabricPlatformPluginManager();
    @Getter
    protected final MessagePlaceHolderManager messagePlaceHolderManager = new FabricMessagePlaceHolderManager();
    protected final LazyHolder<FabricPermissionRegistrationManager> fabricPermissionRegistrationManager = LazyHolder.simple(FabricPermissionRegistrationManager::new);

    protected final LazyHolder<CommandAdapter> commandAdapter;
    protected final FabricPlatformPlayerFactory playerFactory;
    protected final AbstractFabricPlatformServer platformServer;
    @Getter
    protected final IFabricConversionUtil fabricConversionUtil;
    protected final IFabricMessageUtil fabricMessageUtil;

    public VoidFabricLoaderPlugin(
            LazyHolder<CommandAdapter> parserDescriptorFactory,
            FabricPlatformPlayerFactory playerFactory,
            AbstractFabricPlatformServer platformServer,
            IFabricMessageUtil fabricMessageUtil,
            IFabricConversionUtil fabricConversionUtil
    ) {
        this.commandAdapter = parserDescriptorFactory;
        this.playerFactory = playerFactory;
        this.platformServer = platformServer;
        this.fabricMessageUtil = fabricMessageUtil;
        this.fabricConversionUtil = fabricConversionUtil;

        FabricResolverRegistrar resolverRegistrar = new FabricResolverRegistrar();
        VoidExtensionManager extensionManager = VoidAPI.INSTANCE.getExtensionManager();
        resolverRegistrar.registerAll(extensionManager);
        plugin = extensionManager.getPlugin("Void");
    }

    @Override
    public FabricPlatformScheduler getScheduler() {
        return scheduler.get();
    }

    @Override
    public PacketEventsAPI<?> getPacketEvents() {
        return packetEvents;
    }


    @Override
    public ItemResetHandler getItemResetHandler() {
        return itemResetHandler.get();
    }

    @Override
    public SenderFactory<CommandSourceStack> getSenderFactory() {
        return senderFactory.get();
    }

    @Override
    public CommandService getCommandService() {
        return commandService.get();
    }

    @Override
    public VoidPlugin getPlugin() {
        return plugin;
    }

    @Override
    public void registerAPIService() {
        VoidAPIProvider.init(VoidAPI.INSTANCE.getExternalAPI());
    }

    @Override
    public PermissionRegistrationManager getPermissionManager() {
        return fabricPermissionRegistrationManager.get();
    }

    private CommandService createCommandService() {
        try {
            // Accessing CloudHelper triggers the JVM to load CloudCommandService and Cloud classes.
            // If the library is missing, this line throws NoClassDefFoundError immediately.
            return CloudHelper.create(senderFactory.get(), commandAdapter.get());
        } catch (Throwable t) {
            // Catches NoClassDefFoundError (Missing Lib) or other init crashes.
            LogUtil.warn("IMPORTANT: Command Framework failed to load (Missing Cloud Library?). \n" +
                    "Void will run without commands enabled!");

            // Only spam stacktrace if it's weird, not if it's just missing.
            if (!(t instanceof NoClassDefFoundError)) {
                t.printStackTrace();
            }

            // Return No-Op to prevent NullPointers elsewhere
            return () -> {};
        }
    }

    private static class CloudHelper {
        static CommandService create(FabricSenderFactory factory, CommandAdapter commandAdapter) {
            SenderMapper<CommandSourceStack, Sender> mapper = SenderMapper.create(
                    factory::wrap,
                    factory::unwrap
            );
            CommandManager<@NotNull Sender> manager = new FabricServerCommandManager<>(
                    ExecutionCoordinator.simpleCoordinator(),
                    mapper
            );
            CloudCommandAdapter adapter = (CloudCommandAdapter) commandAdapter;
            return new CloudCommandService(() -> manager, adapter);
        }
    }

    public FabricSenderFactory getFabricSenderFactory() {
        return senderFactory.get();
    }

    @Override
    public FabricPlatformPlayerFactory getPlatformPlayerFactory() {
        return playerFactory;
    }

    @Override
    public AbstractFabricPlatformServer getPlatformServer() {
        return platformServer;
    }

    public IFabricMessageUtil getFabricMessageUtils() {
        return fabricMessageUtil;
    }

    public abstract ServerVersion getNativeVersion();
}
