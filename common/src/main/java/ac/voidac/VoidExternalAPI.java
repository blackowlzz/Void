package ac.voidac;

import ac.voidac.api.VoidAbstractAPI;
import ac.voidac.api.VoidUser;
import ac.voidac.api.alerts.AlertManager;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.api.event.EventBus;
import ac.voidac.api.event.events.VoidReloadEvent;
import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.api.storage.backend.BackendRegistry;
import ac.voidac.manager.config.ConfigManagerFileImpl;
import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import ac.voidac.utils.common.ConfigReloadObserver;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

//This is used for void's external API. It has its own class just for organization.

public class VoidExternalAPI implements VoidAbstractAPI, ConfigReloadObserver, StartableInitable {

    // Holder class: VoidExternalAPI is constructed inside VoidAPI's ctor,
    // so a plain static-final would see a null VoidAPI.INSTANCE. Holder
    // init runs on first fire, after VoidAPI is fully built.
    private static final class Channels {
        static final VoidReloadEvent.Channel RELOAD = VoidAPI.INSTANCE.getEventBus().get(VoidReloadEvent.class);
    }

    private final VoidAPI api;
    @Getter
    private final Map<String, Function<VoidUser, String>> variableReplacements = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, String> staticReplacements = new ConcurrentHashMap<>();
    private final Map<String, Function<Object, Object>> functions = new ConcurrentHashMap<>();
    private final ConfigManagerFileImpl configManagerFile = new ConfigManagerFileImpl();
    private ConfigManager configManager = null;
    private boolean started = false;

    public VoidExternalAPI(VoidAPI api) {
        this.api = api;
    }

    @Override
    public @NotNull EventBus getEventBus() {
        return api.getEventBus();
    }

    @Override
    public @Nullable VoidUser getVoidUser(Player player) {
        return getVoidUser(player.getUniqueId());
    }

    @Override
    public @Nullable VoidUser getVoidUser(UUID uuid) {
        return api.getPlayerDataManager().getPlayer(uuid);
    }

    @Override
    public void registerVariable(String string, Function<VoidUser, String> replacement) {
        if (replacement == null) {
            variableReplacements.remove(string);
        } else {
            variableReplacements.put(string, replacement);
        }
    }

    @Override
    public void registerVariable(String variable, String replacement) {
        if (replacement == null) {
            staticReplacements.remove(variable);
        } else {
            staticReplacements.put(variable, replacement);
        }
    }

    @Override
    public String getVoidVersion() {
        return api.getVoidPlugin().getDescription().getVersion();
    }

    @Override
    public void registerFunction(String key, Function<Object, Object> function) {
        if (function == null) {
            functions.remove(key);
        } else {
            functions.put(key, function);
        }
    }

    @Override
    public Function<Object, Object> getFunction(String key) {
        return functions.get(key);
    }

    @Override
    public AlertManager getAlertManager() {
        return VoidAPI.INSTANCE.getAlertManager();
    }

    @Override
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public boolean hasStarted() {
        return started;
    }

    @Override
    public int getCurrentTick() {
        return VoidAPI.INSTANCE.getTickManager().currentTick;
    }

    @Override
    public @NotNull VoidPlugin getVoidPlugin(@NotNull Object o) {
        return this.api.getExtensionManager().getPlugin(o);
    }

    @Override
    public @NotNull BackendRegistry getBackendRegistry() {
        return api.getBackendRegistry();
    }

    // Set when the very first config load fails. A reload later on can fail all
    // it likes, we keep running on the config we already had, but failing here
    // means there is nothing to run on at all.
    private boolean configLoadFailed;

    public boolean isConfigLoadFailed() {
        return configLoadFailed;
    }

    // on load, load the config & register the service
    public void load() {
        // reload() would hand this to the scheduler once started is true, which
        // it never is at this point, so the result comes back synchronously
        configLoadFailed = !successfulReload(configManagerFile);
        api.getLoader().registerAPIService();
    }

    // handles any config loading that's needed to be done after load
    @Override
    public void start() {
        started = true;
        try {
            VoidAPI.INSTANCE.getConfigManager().start();
        } catch (Exception e) {
            LogUtil.error("Failed to start config manager.", e);
        }
        VoidAPI.INSTANCE.getBanWaveManager().init(VoidAPI.INSTANCE.getVoidPlugin().getDataFolder());
        VoidAPI.INSTANCE.getPunishmentDatabase().init(VoidAPI.INSTANCE.getVoidPlugin().getDataFolder());
        VoidAPI.INSTANCE.getVoidBanManager().cleanExpired();
    }

    @Override
    public void reload(ConfigManager config) {
        if (config.isLoadedAsync() && started) {
            VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(VoidAPI.INSTANCE.getVoidPlugin(),
                    () -> successfulReload(config));
        } else {
            successfulReload(config);
        }
    }

    @Override
    public CompletableFuture<Boolean> reloadAsync(ConfigManager config) {
        if (config.isLoadedAsync() && started) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(VoidAPI.INSTANCE.getVoidPlugin(),
                    () -> future.complete(successfulReload(config)));
            return future;
        }
        return CompletableFuture.completedFuture(successfulReload(config));
    }

    private boolean successfulReload(ConfigManager config) {
        try {
            config.reload();
            VoidAPI.INSTANCE.getConfigManager().load(config);
            if (started) VoidAPI.INSTANCE.getConfigManager().start();
            onReload(config);
            if (started)
                VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(VoidAPI.INSTANCE.getVoidPlugin(),
                        () -> Channels.RELOAD.fire(true));
            return true;
        } catch (Exception e) {
            LogUtil.error("Failed to reload config", e);
        }
        if (started)
            VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(VoidAPI.INSTANCE.getVoidPlugin(),
                    () -> Channels.RELOAD.fire(false));
        return false;
    }

    @Override
    public void onReload(ConfigManager newConfig) {
        if (newConfig == null) {
            LogUtil.warn("ConfigManager not set. Using default config file manager.");
            configManager = configManagerFile;
        } else {
            configManager = newConfig;
        }
        // Update variables
        updateVariables();
        // Restart
        VoidAPI.INSTANCE.getAlertManager().reload(configManager);
        VoidAPI.INSTANCE.getDiscordManager().reload();
        VoidAPI.INSTANCE.getSpectateManager().reload();
        VoidAPI.INSTANCE.getBanWaveManager().reload(configManager);
        VoidAPI.INSTANCE.getBridgeClient().reload(configManager);
        // First-load guard: load() calls reload() before start() runs, so this fires once with started=false before the datastore exists. Subsequent /void reload calls see started=true and proceed (including disabled→enabled flips, DataStoreLifecycle.reload() re-evaluates builder.enabled() each time).
        if (!started) return;
        // Hot-reload picks up backend swaps + routing + connection-pool edits without a server restart. Drains in-flight writes for shutdown-drain-timeout-ms then drops; brief mid-reload unavailability is the tradeoff.
        if (VoidAPI.INSTANCE.getDataStoreLifecycle() != null) {
            VoidAPI.INSTANCE.getDataStoreLifecycle().reload();
        }
        // Reload checks for all players
        for (VoidPlayer player : VoidAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.runSafely(() -> player.reload(configManager));
        }
    }

    private void updateVariables() {
        variableReplacements.putIfAbsent("%player%", VoidUser::getName);
        variableReplacements.putIfAbsent("%uuid%", user -> user.getUniqueId().toString());
        variableReplacements.putIfAbsent("%ping%", user -> user.getTransactionPing() + "");
        variableReplacements.putIfAbsent("%brand%", VoidUser::getBrand);
        variableReplacements.putIfAbsent("%h_sensitivity%", user -> ((int) Math.round(user.getHorizontalSensitivity() * 200)) + "");
        variableReplacements.putIfAbsent("%v_sensitivity%", user -> ((int) Math.round(user.getVerticalSensitivity() * 200)) + "");
        variableReplacements.putIfAbsent("%fast_math%", user -> !user.isVanillaMath() + "");
        variableReplacements.putIfAbsent("%tps%", user -> String.format("%.2f", VoidAPI.INSTANCE.getPlatformServer().getTPS()));
        variableReplacements.putIfAbsent("%version%", VoidUser::getVersionName);
        // static variables
        staticReplacements.put("%prefix%", MessageUtil.translateAlternateColorCodes('&', VoidAPI.INSTANCE.getConfigManager().getPrefix()));
        staticReplacements.putIfAbsent("%void_version%", getVoidVersion());
    }
}
