package ac.voidac.platform.bukkit;

import ac.voidac.VoidAPI;
import ac.voidac.VoidExternalAPI;
import ac.voidac.api.VoidAPIProvider;
import ac.voidac.api.VoidAbstractAPI;
import ac.voidac.api.event.EventBus;
import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.command.CloudCommandService;
import ac.voidac.internal.platform.bukkit.resolver.BukkitResolverRegistrar;
import ac.voidac.checks.impl.exploit.AntiXray;
import ac.voidac.manager.init.Initable;
import ac.voidac.manager.init.start.ExemptOnlinePlayersOnReload;
import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.platform.api.Platform;
import ac.voidac.platform.api.PlatformLoader;
import ac.voidac.platform.api.PlatformServer;
import ac.voidac.platform.api.command.CommandService;
import ac.voidac.platform.api.manager.ItemResetHandler;
import ac.voidac.platform.api.manager.MessagePlaceHolderManager;
import ac.voidac.platform.api.manager.PlatformPluginManager;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.player.PlatformPlayerFactory;
import ac.voidac.platform.api.scheduler.PlatformScheduler;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.platform.api.sender.SenderFactory;
import ac.voidac.platform.bukkit.initables.BukkitBStats;
import ac.voidac.platform.bukkit.initables.BukkitEventManager;
import ac.voidac.platform.bukkit.initables.BukkitTickEndEvent;
import ac.voidac.platform.bukkit.initables.UpdateChecker;
import ac.voidac.platform.bukkit.manager.BukkitItemResetHandler;
import ac.voidac.platform.bukkit.manager.BukkitMessagePlaceHolderManager;
import ac.voidac.platform.bukkit.manager.BukkitParserDescriptorFactory;
import ac.voidac.platform.bukkit.manager.BukkitPermissionRegistrationManager;
import ac.voidac.platform.bukkit.manager.BukkitPlatformPluginManager;
import ac.voidac.platform.bukkit.player.BukkitPlatformPlayerFactory;
import ac.voidac.platform.bukkit.scheduler.bukkit.BukkitPlatformScheduler;
import ac.voidac.platform.bukkit.scheduler.folia.FoliaPlatformScheduler;
import ac.voidac.platform.bukkit.sender.BukkitSenderFactory;
import ac.voidac.platform.bukkit.utils.placeholder.PlaceholderAPIExpansion;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.brigadier.BrigadierSetting;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

public class VoidBukkitLoaderPlugin extends JavaPlugin implements PlatformLoader {

    public static VoidBukkitLoaderPlugin LOADER;

    private final LazyHolder<PlatformScheduler> scheduler = LazyHolder.simple(this::createScheduler);
    private final LazyHolder<PacketEventsAPI<?>> packetEvents = LazyHolder.simple(() -> SpigotPacketEventsBuilder.build(this));
    private final LazyHolder<BukkitSenderFactory> senderFactory = LazyHolder.simple(BukkitSenderFactory::new);
    private final LazyHolder<ItemResetHandler> itemResetHandler = LazyHolder.simple(BukkitItemResetHandler::new);
    private final LazyHolder<CommandService> commandService = LazyHolder.simple(this::createCommandService);
    private final CloudCommandAdapter commandAdapter = new BukkitParserDescriptorFactory();
    @Getter private final PlatformPlayerFactory platformPlayerFactory = new BukkitPlatformPlayerFactory();
    @Getter private final PlatformPluginManager pluginManager = new BukkitPlatformPluginManager();
    @Getter private final VoidPlugin plugin;
    @Getter private final PlatformServer platformServer = new BukkitPlatformServer();
    @Getter private final MessagePlaceHolderManager messagePlaceHolderManager = new BukkitMessagePlaceHolderManager();
    @Getter private final BukkitPermissionRegistrationManager permissionManager = new BukkitPermissionRegistrationManager();

    public VoidBukkitLoaderPlugin() {
        BukkitResolverRegistrar registrar = new BukkitResolverRegistrar();
        registrar.registerAll(VoidAPI.INSTANCE.getExtensionManager());
        this.plugin = registrar.resolvePlugin(this);
    }

    @Override
    public void onLoad() {
        LOADER = this;
        VoidAPI.INSTANCE.load(this, this.getBukkitInitTasks());
    }

    private Initable[] getBukkitInitTasks() {
        return new Initable[] {
                new AntiXray(),
                new ExemptOnlinePlayersOnReload(),
                new BukkitEventManager(),
                new BukkitTickEndEvent(),
                new BukkitBStats(),
                new UpdateChecker(),
                (StartableInitable) () -> {
                    if (BukkitMessagePlaceHolderManager.hasPlaceholderAPI) {
                        new PlaceholderAPIExpansion().register();
                    }
                }
        };
    }

    @Override
    public void onEnable() {
        VoidAPI.INSTANCE.start();
    }

    @Override
    public void onDisable() {
        VoidAPI.INSTANCE.getPlayerDataManager().getEntries().forEach(player -> {
            try {
                player.storageEspDecoyManager.restoreAllDecoys();
                player.storageEspDecoyManager.shutdown();
            } catch (Exception ignored) {
            }
        });
        if (VoidAPI.INSTANCE.isInitialized()
            && VoidAPI.INSTANCE.getInitManager() != null
            && VoidAPI.INSTANCE.getInitManager().isStarted()
            && !VoidAPI.INSTANCE.getInitManager().isStopped()) {
            VoidAPI.INSTANCE.stop();
        }
    }

    @Override
    public PlatformScheduler getScheduler() {
        return scheduler.get();
    }

    @Override
    public PacketEventsAPI<?> getPacketEvents() {
        return packetEvents.get();
    }

    @Override
    public ItemResetHandler getItemResetHandler() {
        return itemResetHandler.get();
    }

    @Override
    public CommandService getCommandService() {
        return commandService.get();
    }

    @Override
    public SenderFactory<CommandSender> getSenderFactory() {
        return senderFactory.get();
    }

    @Override
    @SuppressWarnings("removal")
    public void registerAPIService() {
        final VoidExternalAPI externalAPI = VoidAPI.INSTANCE.getExternalAPI();
        final EventBus eventBus = externalAPI.getEventBus();
        final ac.voidac.api.plugin.VoidPlugin plugin = VoidAPI.INSTANCE.getVoidPlugin();

        // Bridge Void events → legacy Bukkit Event API so pre-1.3 plugins that
        // listened for ac.voidac.api.events.* Bukkit events keep working.
        // Typed channel subscriptions here are plugin-bound so they go away if
        // Void itself is disabled.

        eventBus.get(ac.voidac.api.event.events.VoidJoinEvent.class).onJoin(plugin, (user) -> {
            Bukkit.getPluginManager().callEvent(new ac.voidac.api.events.VoidJoinEvent(user));
        });

        eventBus.get(ac.voidac.api.event.events.VoidQuitEvent.class).onQuit(plugin, (user) -> {
            Bukkit.getPluginManager().callEvent(new ac.voidac.api.events.VoidQuitEvent(user));
        });

        eventBus.get(ac.voidac.api.event.events.VoidReloadEvent.class).onReload(plugin, (success) -> {
            Bukkit.getPluginManager().callEvent(new ac.voidac.api.events.VoidReloadEvent(success));
        });

        eventBus.get(ac.voidac.api.event.events.FlagEvent.class).onFlag(plugin, (user, check, verbose, cancelled) -> {
            ac.voidac.api.events.FlagEvent bukkitEvent =
                    new ac.voidac.api.events.FlagEvent(user, check, verbose);
            Bukkit.getPluginManager().callEvent(bukkitEvent);
            return cancelled || bukkitEvent.isCancelled();
        });

        eventBus.get(ac.voidac.api.event.events.CommandExecuteEvent.class).onCommandExecute(plugin, (user, check, verbose, command, cancelled) -> {
            ac.voidac.api.events.CommandExecuteEvent bukkitEvent =
                    new ac.voidac.api.events.CommandExecuteEvent(user, check, verbose, command);
            Bukkit.getPluginManager().callEvent(bukkitEvent);
            return cancelled || bukkitEvent.isCancelled();
        });

        eventBus.get(ac.voidac.api.event.events.CompletePredictionEvent.class).onCompletePrediction(plugin, (user, check, offset, cancelled) -> {
            // Legacy Bukkit event has a verbose field that the new channel event does not; pass empty.
            ac.voidac.api.events.CompletePredictionEvent bukkitEvent =
                    new ac.voidac.api.events.CompletePredictionEvent(user, check, "", offset);
            Bukkit.getPluginManager().callEvent(bukkitEvent);
            return cancelled || bukkitEvent.isCancelled();
        });

        VoidAPIProvider.init(externalAPI);
        Bukkit.getServicesManager().register(VoidAbstractAPI.class, externalAPI, this, ServicePriority.Normal);
    }

    private PlatformScheduler createScheduler() {
        return VoidAPI.INSTANCE.getPlatform() == Platform.FOLIA ? new FoliaPlatformScheduler() : new BukkitPlatformScheduler();
    }

    private CommandService createCommandService() {
        try {
            return new CloudCommandService(this::createCloudCommandManager, commandAdapter);
        } catch (Throwable t) {
            LogUtil.warn("CRITICAL: Failed to initialize Command Framework. " +
                    "Void will continue to run with no commands.", t);
            return () -> {};
        }
    }

    private CommandManager<Sender> createCloudCommandManager() {
        LegacyPaperCommandManager<Sender> manager = new LegacyPaperCommandManager<>(
                this,
                ExecutionCoordinator.simpleCoordinator(),
                senderFactory.get()
        );
        if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            try {
                manager.registerBrigadier();
                CloudBrigadierManager<Sender, ?> cbm = manager.brigadierManager();
                cbm.settings().set(BrigadierSetting.FORCE_EXECUTABLE, true);
            } catch (Throwable t) {
                LogUtil.error("Failed to register Brigadier native completions. Falling back to standard completions.", t);
            }
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }
        return manager;
    }

    public BukkitSenderFactory getBukkitSenderFactory() {
        return senderFactory.get();
    }
}
