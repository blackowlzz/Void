package ac.voidac;

import ac.voidac.api.event.EventBus;
import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.api.storage.backend.BackendRegistry;
import ac.voidac.internal.plugin.resolver.VoidExtensionManager;
import ac.voidac.internal.event.OptimizedEventBus;
import ac.voidac.internal.storage.backend.BackendRegistryImpl;
import ac.voidac.internal.storage.backend.memory.InMemoryBackendProvider;
import ac.voidac.internal.storage.backend.mongo.MongoBackendProvider;
import ac.voidac.internal.storage.backend.mysql.MysqlBackendProvider;
import ac.voidac.internal.storage.backend.postgres.PostgresBackendProvider;
import ac.voidac.internal.storage.backend.redis.RedisBackendProvider;
import ac.voidac.internal.storage.backend.sqlite.SqliteBackendProvider;
import ac.voidac.manager.AlertManagerImpl;
import ac.voidac.manager.BanWaveManager;
import ac.voidac.manager.DiscordManager;
import ac.voidac.manager.InitManager;
import ac.voidac.manager.SpectateManager;
import ac.voidac.manager.ThresholdOptimizerManager;
import ac.voidac.manager.TickManager;
import ac.voidac.manager.VoidBanManager;
import ac.voidac.manager.config.BaseConfigManager;
import ac.voidac.bridge.BridgeClient;
import ac.voidac.manager.punishment.PunishmentDatabase;
import ac.voidac.manager.datastore.DataStoreLifecycle;
import ac.voidac.manager.init.Initable;
import ac.voidac.platform.api.Platform;
import ac.voidac.platform.api.PlatformLoader;
import ac.voidac.platform.api.PlatformServer;
import ac.voidac.platform.api.command.CommandService;
import ac.voidac.platform.api.manager.ItemResetHandler;
import ac.voidac.platform.api.manager.MessagePlaceHolderManager;
import ac.voidac.platform.api.manager.PermissionRegistrationManager;
import ac.voidac.platform.api.manager.PlatformPluginManager;
import ac.voidac.platform.api.player.PlatformPlayerFactory;
import ac.voidac.platform.api.scheduler.PlatformScheduler;
import ac.voidac.platform.api.sender.SenderFactory;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.PlayerDataManager;
import ac.voidac.utils.common.arguments.CommonVoidArguments;
import ac.voidac.utils.reflection.ReflectionUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;


@Getter
public final class VoidAPI {
    public static final VoidAPI INSTANCE = new VoidAPI();

    @Getter
    private final Platform platform = detectPlatform();
    private final BaseConfigManager configManager;
    private final AlertManagerImpl alertManager;
    private final SpectateManager spectateManager;
    private final DiscordManager discordManager;
    private final BanWaveManager banWaveManager;
    private final PunishmentDatabase punishmentDatabase;
    private final VoidBanManager voidBanManager;
    private final BridgeClient bridgeClient;
    private final ThresholdOptimizerManager thresholdOptimizer;
    private final PlayerDataManager playerDataManager;
    private final TickManager tickManager;
    private final VoidExtensionManager extensionManager;
    private final EventBus eventBus;
    private final VoidExternalAPI externalAPI;
    private DataStoreLifecycle dataStoreLifecycle;
    private final BackendRegistry backendRegistry = buildBackendRegistry();
    private PlatformLoader loader;
    @Getter
    private InitManager initManager;
    private boolean initialized = false;

    private VoidAPI() {
        this.configManager = new BaseConfigManager();
        this.alertManager = new AlertManagerImpl();
        this.spectateManager = new SpectateManager();
        this.discordManager = new DiscordManager();
        this.banWaveManager = new BanWaveManager();
        this.punishmentDatabase = new PunishmentDatabase();
        this.voidBanManager = new VoidBanManager();
        this.bridgeClient = new BridgeClient();
        this.thresholdOptimizer = new ThresholdOptimizerManager();
        this.playerDataManager = new PlayerDataManager();
        this.tickManager = new TickManager();
        this.extensionManager = new VoidExtensionManager();
        this.eventBus = new OptimizedEventBus(extensionManager);
        this.externalAPI = new VoidExternalAPI(this);
    }

    // the order matters
    private static Platform detectPlatform() {
        Platform override = CommonVoidArguments.PLATFORM_OVERRIDE.value();
        if (override != null) return override;
        if (ReflectionUtils.hasClass("io.papermc.paper.threadedregions.RegionizedServer")) return Platform.FOLIA;
        if (ReflectionUtils.hasClass("org.bukkit.Bukkit")) return Platform.BUKKIT;
        if (ReflectionUtils.hasClass("net.fabricmc.loader.api.FabricLoader")) return Platform.FABRIC;
        throw new IllegalStateException("Unknown platform!");
    }

    public void load(PlatformLoader platformLoader, Initable... platformSpecificInitables) {
        this.loader = platformLoader;
        this.dataStoreLifecycle = new DataStoreLifecycle(getVoidPlugin(), backendRegistry);
        this.initManager = new InitManager(loader.getPacketEvents(), platformSpecificInitables);
        this.initManager.load();
        this.initialized = true;
    }

    private static BackendRegistry buildBackendRegistry() {
        BackendRegistryImpl registry = new BackendRegistryImpl();
        registry.register(new SqliteBackendProvider());
        registry.register(new InMemoryBackendProvider());
        registry.register(new MysqlBackendProvider());
        registry.register(new PostgresBackendProvider());
        registry.register(new MongoBackendProvider());
        registry.register(new RedisBackendProvider());
        return registry;
    }

    public void start() {
        checkInitialized();
        printStartupBanner();
        initManager.start();
    }

    private static void printStartupBanner() {
        LogUtil.console("&d____   ____    .__    .___ _____  _________   ");
        LogUtil.console("&d\\   \\ /   /___ |__| __| _//  _  \\ \\_   ___ \\  ");
        LogUtil.console("&d \\   Y   /  _ \\|  |/ __ |/  /_\\  \\/    \\  \\/  ");
        LogUtil.console("&d  \\     (  <_> )  / /_/ /    |    \\     \\____  ");
        LogUtil.console("&d   \\___/ \\____/|__\\____ \\____|__  /\\______  /  ");
        LogUtil.console("&d                        \\/       \\/        \\/   ");
        LogUtil.console(" ");
        LogUtil.console("&a  Modrinth: &fhttps://modrinth.com/plugin/voidac");
        LogUtil.console("&b  Discord:  &fhttps://discord.gg/DRnhHrcseU");
        LogUtil.console(" ");
    }

    public void stop() {
        checkInitialized();
        initManager.stop();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public PlatformScheduler getScheduler() {
        return loader.getScheduler();
    }

    public PlatformPlayerFactory getPlatformPlayerFactory() {
        return loader.getPlatformPlayerFactory();
    }

    public VoidPlugin getVoidPlugin() {
        return loader.getPlugin();
    }

    public SenderFactory<?> getSenderFactory() {
        return loader.getSenderFactory();
    }

    public ItemResetHandler getItemResetHandler() {
        return loader.getItemResetHandler();
    }

    public PlatformPluginManager getPluginManager() {
        return loader.getPluginManager();
    }

    public PlatformServer getPlatformServer() {
        return loader.getPlatformServer();
    }

    public @NotNull MessagePlaceHolderManager getMessagePlaceHolderManager() {
        return loader.getMessagePlaceHolderManager();
    }

    public CommandService getCommandService() {
        return loader.getCommandService();
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("VoidAPI has not been initialized!");
        }
    }

    public PermissionRegistrationManager getPermissionManager() {
        return loader.getPermissionManager();
    }

    public VoidExtensionManager getExtensionManager() {
        return extensionManager;
    }
}
