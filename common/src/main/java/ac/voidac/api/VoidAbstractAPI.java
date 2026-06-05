package ac.voidac.api;

import ac.voidac.api.alerts.AlertManager;
import ac.voidac.api.common.BasicReloadable;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.api.config.ConfigReloadable;
import ac.voidac.api.event.EventBus;
import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.api.storage.backend.BackendRegistry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface VoidAbstractAPI extends ConfigReloadable, BasicReloadable {
    /**
     * Returns EventBus instanced used to register events and listen to Void events
     * @return {@link EventBus}
     */
    @NotNull EventBus getEventBus();

    /**
     * Retrieves a VoidUser reference from the player.
     * @param player Bukkit player reference
     * @return VoidUser
     */
    @Nullable
    @Deprecated
    VoidUser getVoidUser(Player player);

    /**
     * Retrieves a VoidUser reference from the player's UUID.
     * @param uuid UUID of the player
     * @return VoidUser
     */
    @Nullable VoidUser getVoidUser(UUID uuid);

    /**
     * Used to create or replace variables, such as %player%. This only works
     * for player related messages.
     * @param variable
     * @param replacement
     */
    void registerVariable(String variable, @Nullable Function<VoidUser, String> replacement);

    /**
     * Used to create or replace static variables, such as %server%.
     * @param variable
     * @param replacement
     */
    void registerVariable(String variable, @Nullable String replacement);

    /**
     * Retrieves the plugin version of Void.
     * @return Void version
     */
    String getVoidVersion();

    /**
     * Used for future expansion. Don't use this unless you know what you're doing.
     */
    void registerFunction(String key, @Nullable Function<Object, Object> function);

    /**
     * Used for future expansion. Don't use this unless you know what you're doing.
     */
    @Nullable Function<Object, Object> getFunction(String key);

    /**
     * Retrieves the alert manager.
     * @return AlertManager
     */
    AlertManager getAlertManager();

    /**
     * Retrieves the config manager.
     * @return Configurable
     */
    ConfigManager getConfigManager();

    /**
     * Reloads Void using the config file.
     */
    @Override
    default void reload() {
        reload(getConfigManager());
    }

    /**
     * Reloads Void asynchronously using the config file.
     * @return CompletableFuture
     */
    default CompletableFuture<Boolean> reloadAsync() {
        return reloadAsync(getConfigManager());
    }

    /**
     * Checks if the API has reached the start phase of the plugin.
     * @return boolean
     */
    boolean hasStarted();

    /**
     * Retrieves the current tick of the server.
     * @return int
     */
    int getCurrentTick();

    /**
     * Resolves a platform-specific object into a {@link VoidPlugin} wrapper.
     * <p>
     * This method is the bridge between platform-specific objects (like a Bukkit {@code JavaPlugin})
     * and the universal Void API.
     * <p>
     * <b>Supported Context Types:</b>
     * <ul>
     *     <li><b>Bukkit:</b> Your {@code JavaPlugin} instance (e.g., {@code this}).</li>
     *     <li><b>Fabric:</b> Your {@code ModInitializer} instance, {@code ModContainer}, or Mod ID (String).</li>
     *     <li><b>Universal:</b> Any {@code Class<?>} belonging to your plugin/mod (resolves the providing container).</li>
     * </ul>
     * <p>
     * <b>Performance Note:</b>
     * While convenience methods in the {@link EventBus} accept generic Objects, they perform this resolution
     * check every time they are called. If you are performing frequent operations, it is recommended to
     * call this method once, cache the {@link VoidPlugin} result, and pass that to the API instead.
     *
     * @param platformContext The platform-specific context (e.g., {@code this}).
     * @return The resolved VoidPlugin wrapper.
     * @throws IllegalArgumentException If the provided context is not a valid plugin, mod, or class known to the platform.
     */
    @NotNull VoidPlugin getVoidPlugin(@NotNull Object platformContext);

    /**
     * Returns the mutable registry of storage {@code BackendProvider}s. External extensions
     * register their providers here before the data store starts, enabling them to contribute
     * custom storage engines (e.g. MySQL, Postgres, Redis) without forking the platform.
     * <p>
     * Registrations made after the data store has started do not retroactively affect its
     * already-built routing; register during plugin load, or before the Void plugin finishes
     * enabling.
     */
    @NotNull BackendRegistry getBackendRegistry();

}
