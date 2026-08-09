package ac.voidac.bridge.proxy.bungee;

import ac.voidac.bridge.proxy.BanRegistry;
import ac.voidac.bridge.proxy.BridgeConfig;
import ac.voidac.bridge.proxy.BridgeCore;
import ac.voidac.bridge.proxy.LegacyColors;
import ac.voidac.bridge.proxy.ProxyPlatform;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;

/** Same BridgeCore as Velocity, just different event names and a worse component API. */
public final class VoidBridgeBungee extends Plugin implements Listener, ProxyPlatform {

    private @Nullable BridgeCore core;

    @Override
    public void onEnable() {
        Path dataDirectory = getDataFolder().toPath();

        BridgeConfig config = BridgeConfig.load(dataDirectory, this);
        if (config == null) {
            getLogger().warning("void-bridge is loaded but doing nothing. See above.");
            return;
        }

        BanRegistry registry = new BanRegistry(dataDirectory, this);
        registry.load();
        registry.pruneExpired();

        this.core = new BridgeCore(this, config, registry);
        this.core.start();
        ProxyServer.getInstance().getPluginManager().registerListener(this, this);
    }

    @Override
    public void onDisable() {
        if (core != null) core.stop();
    }


    @EventHandler
    public void onLogin(LoginEvent event) {
        BridgeCore active = core;
        if (active == null) return;

        String name = event.getConnection().getName();
        UUID uuid = event.getConnection().getUniqueId();

        BanRegistry.Entry ban = active.banFor(uuid, name, null);
        if (ban == null || !active.shouldDenyLogin(ban)) return;

        event.setCancelled(true);
        event.setCancelReason(text(active.banScreen(ban)));
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        BridgeCore active = core;
        if (active == null) return;

        ProxiedPlayer player = event.getPlayer();
        BanRegistry.Entry ban = active.banFor(
                player.getUniqueId(), player.getName(), event.getTarget().getName());
        if (ban == null) return;

        event.setCancelled(true);
        // cancelling their first connect leaves them staring at nothing, so finish the job
        if (player.getServer() == null) {
            player.disconnect(text(active.banScreen(ban)));
        } else {
            player.sendMessage(text(active.banScreen(ban)));
        }
    }

    @Override
    public @NotNull Path dataDirectory() {
        return getDataFolder().toPath();
    }

    @Override
    public @NotNull Collection<String> serverNames() {
        return new ArrayList<>(ProxyServer.getInstance().getServers().keySet());
    }


    @Override
    public void disconnect(@NotNull UUID playerUuid, @NotNull String reason) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerUuid);
        if (player != null) player.disconnect(text(reason));
    }


    @Override
    public void info(@NotNull String message) {
        getLogger().info(message);
    }

    @Override
    public void warn(@NotNull String message, @Nullable Throwable error) {
        if (error != null) {
            getLogger().log(Level.WARNING, message, error);
        } else {
            getLogger().warning(message);
        }
    }

    private static BaseComponent[] text(String legacy) {
        return TextComponent.fromLegacyText(LegacyColors.translate(legacy));
    }
}
