package ac.voidac.bridge.proxy.velocity;

import ac.voidac.bridge.proxy.BanRegistry;
import ac.voidac.bridge.proxy.BridgeConfig;
import ac.voidac.bridge.proxy.BridgeCore;
import ac.voidac.bridge.proxy.LegacyColors;
import ac.voidac.bridge.proxy.ProxyPlatform;
import com.google.inject.Inject;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Velocity entrypoint.
 * Metadata is in velocity-plugin.json rather than the @Plugin annotation so the
 * build can stamp the version instead of us hardcoding it twice.
 */
public final class VoidBridgeVelocity implements ProxyPlatform {


    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private @Nullable BridgeCore core;

    @Inject
    public VoidBridgeVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        BridgeConfig config = BridgeConfig.load(dataDirectory, this);
        if (config == null) {
            logger.warn("void-bridge is loaded but doing nothing. See above.");
            return;
        }

        BanRegistry registry = new BanRegistry(dataDirectory, this);
        registry.load();
        registry.pruneExpired();

        this.core = new BridgeCore(this, config, registry);
        this.core.start();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (core != null) core.stop();
    }


    @Subscribe
    public void onLogin(LoginEvent event) {
        BridgeCore active = core;
        if (active == null) return;

        Player player = event.getPlayer();
        BanRegistry.Entry ban = active.banFor(player.getUniqueId(), player.getUsername(), null);
        if (ban == null || !active.shouldDenyLogin(ban)) return;

        event.setResult(ResultedEvent.ComponentResult.denied(text(active.banScreen(ban))));
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        BridgeCore active = core;
        if (active == null) return;

        RegisteredServer target = event.getOriginalServer();
        Player player = event.getPlayer();

        BanRegistry.Entry ban = active.banFor(
                player.getUniqueId(), player.getUsername(), target.getServerInfo().getName());
        if (ban == null) return;

        // scoped ban bounces them off those servers only, rest of the network is fine
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        player.sendMessage(text(active.banScreen(ban)));
    }

    @Override
    public @NotNull Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public @NotNull Collection<String> serverNames() {
        Collection<String> names = new ArrayList<>();
        for (RegisteredServer registered : server.getAllServers()) {
            names.add(registered.getServerInfo().getName());
        }
        return names;
    }


    @Override
    public void disconnect(@NotNull UUID playerUuid, @NotNull String reason) {
        server.getPlayer(playerUuid).ifPresent(player -> player.disconnect(text(reason)));
    }


    @Override
    public void info(@NotNull String message) {
        logger.info(message);
    }

    @Override
    public void warn(@NotNull String message, @Nullable Throwable error) {
        if (error != null) {
            logger.warn(message, error);
        } else {
            logger.warn(message);
        }
    }

    private static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection()
                .deserialize(LegacyColors.translate(legacy));
    }
}
