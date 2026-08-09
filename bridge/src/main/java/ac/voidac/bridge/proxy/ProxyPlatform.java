package ac.voidac.bridge.proxy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

/**
 * The four things BridgeCore needs from whatever proxy it landed on.
 * Velocity and Bungee disagree about everything else, but not these.
 */
public interface ProxyPlatform {

    @NotNull Path dataDirectory();

    @NotNull Collection<String> serverNames();

    /** Boots a player who is on the network when a ban covering them arrives. */
    void disconnect(@NotNull UUID playerUuid, @NotNull String reason);

    void info(@NotNull String message);

    void warn(@NotNull String message, @Nullable Throwable error);
}
