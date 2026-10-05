package ac.voidac.compat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.netty.NettyManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketEventsServerVersionTest {
    @ParameterizedTest
    @CsvSource({
            "26.3.build.49-alpha, 26.3",
            "26.3-R0.1-SNAPSHOT, 26.3",
            "26.2-R0.1-SNAPSHOT, 26.2",
            "1.21.11-R0.1-SNAPSHOT, 1.21.11",
            "1.8.8-R0.1-SNAPSHOT, 1.8.8"
    })
    void detectsServerVersionWithBundledPacketEvents(String reportedVersion, String expectedVersion) throws Exception {
        Class<?> serverClass = Class.forName("org.bukkit.Server");
        Field serverField = Class.forName("org.bukkit.Bukkit").getDeclaredField("server");
        serverField.setAccessible(true);
        Object originalServer = serverField.get(null);
        PacketEventsAPI<?> originalApi = PacketEvents.getAPI();
        ServerManager manager = (ServerManager) Class.forName(
                "io.github.retrooper.packetevents.manager.server.ServerManagerImpl").getConstructor().newInstance();
        try {
            serverField.set(null, Proxy.newProxyInstance(serverClass.getClassLoader(), new Class<?>[]{serverClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getBukkitVersion")) return reportedVersion;
                        throw new UnsupportedOperationException(method.getName());
                    }));
            PacketEvents.setAPI(new PacketEventsAPI<>() {
                public boolean isLoaded() { return true; }
                public void init() { }
                public boolean isInitialized() { return true; }
                public boolean isTerminated() { return false; }
                public Object getPlugin() { return null; }
                public ServerManager getServerManager() { return manager; }
                public ProtocolManager getProtocolManager() { return null; }
                public PlayerManager getPlayerManager() { return null; }
                public NettyManager getNettyManager() { return null; }
                public ChannelInjector getInjector() { return null; }
            });
            assertEquals(expectedVersion, manager.getVersion().getReleaseName());
            assertEquals(manager.getVersion().getProtocolVersion(), manager.getVersion().toClientVersion().getProtocolVersion());
        } finally {
            serverField.set(null, originalServer);
            PacketEvents.setAPI(originalApi);
        }
    }
}
