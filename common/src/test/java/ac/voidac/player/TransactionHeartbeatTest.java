package ac.voidac.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import io.netty.channel.embedded.EmbeddedChannel;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TransactionHeartbeatTest {
    private ServerVersion version;

    @BeforeEach
    void initializeProtocol() {
        System.setProperty("VoidPlatformOverride", "BUKKIT");
        version = ServerVersion.V_26_2;
        PacketEvents.setAPI(new PacketEventsAPI<>() {
            public boolean isLoaded() { return true; }
            public void init() { }
            public boolean isInitialized() { return true; }
            public boolean isTerminated() { return false; }
            public Object getPlugin() { return null; }
            public ServerManager getServerManager() { return () -> version; }
            public ProtocolManager getProtocolManager() { return null; }
            public PlayerManager getPlayerManager() { return null; }
            public NettyManager getNettyManager() { return new NettyManagerImpl(); }
            public ChannelInjector getInjector() { return null; }
        });
    }

    @Test
    void heartbeatReachesIdleChannelWithoutAnotherServerPacket() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.player.sendTransaction(true, true);
            assertNull(fixture.channel.readOutbound());
            assertTrue(fixture.player.didWeSendThatTrans.isEmpty());
            fixture.player.tasks.remove().run();
            assertInstanceOf(WrapperPlayServerPing.class, fixture.channel.readOutbound());
            assertEquals(1, fixture.player.didWeSendThatTrans.size());
        }
    }

    @Test
    void heartbeatFlushesPendingTransactionsInTheirOriginalOrder() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.player.sendTransaction();
            assertNull(fixture.channel.readOutbound());
            fixture.player.sendTransaction(true, true);
            fixture.player.tasks.remove().run();
            WrapperPlayServerPing first = fixture.channel.readOutbound();
            WrapperPlayServerPing second = fixture.channel.readOutbound();
            assertEquals(-100, first.getId());
            assertTrue(second.getId() < first.getId());
            assertEquals(2, fixture.player.didWeSendThatTrans.size());
            assertNull(fixture.channel.readOutbound());
        }
    }

    @Test
    void ordinaryAsyncTransactionsKeepServerFlushOrdering() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.player.sendTransaction(true);
            fixture.player.tasks.remove().run();
            assertNull(fixture.channel.readOutbound());
            fixture.channel.flush();
            assertInstanceOf(WrapperPlayServerPing.class, fixture.channel.readOutbound());
        }
    }

    @Test
    void legacyHeartbeatUsesWindowConfirmation() throws Exception {
        version = ServerVersion.V_1_8;
        try (Fixture fixture = new Fixture()) {
            fixture.player.sendTransaction(true, true);
            fixture.player.tasks.remove().run();
            WrapperPlayServerWindowConfirmation packet = fixture.channel.readOutbound();
            assertEquals(-100, packet.getActionId());
        }
    }

    @Test
    void configurationPhaseDoesNotSendPlayHeartbeats() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.user.setEncoderState(ConnectionState.CONFIGURATION);
            fixture.player.sendTransaction(true, true);
            assertTrue(fixture.player.tasks.isEmpty());
            assertTrue(fixture.player.didWeSendThatTrans.isEmpty());
            assertNull(fixture.channel.readOutbound());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final EmbeddedChannel channel = new EmbeddedChannel();
        final TestPlayer player;
        final User user;

        Fixture() throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            player = (TestPlayer) ((Unsafe) field.get(null)).allocateInstance(TestPlayer.class);
            player.tasks = new ArrayDeque<>();
            set("transactionIDCounter", new AtomicInteger(100));
            set("didWeSendThatTrans", new HashSet<Short>());
            user = new User(channel, ConnectionState.PLAY, ClientVersion.V_26_2,
                    new UserProfile(UUID.randomUUID(), "HeartbeatTest")) {
                @Override
                public void writePacket(PacketWrapper<?> packet) {
                    assertRegistered(packet);
                    channel.write(packet);
                }

                @Override
                public void sendPacket(PacketWrapper<?> packet) {
                    assertRegistered(packet);
                    channel.writeAndFlush(packet);
                }

                private void assertRegistered(PacketWrapper<?> packet) {
                    short id = packet instanceof WrapperPlayServerPing ping ? (short) ping.getId()
                            : ((WrapperPlayServerWindowConfirmation) packet).getActionId();
                    assertTrue(player.didWeSendThatTrans.contains(id));
                }
            };
            set("user", user);
        }

        private void set(String name, Object value) throws Exception {
            Field field = VoidPlayer.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(player, value);
        }

        public void close() {
            channel.finishAndReleaseAll();
        }
    }

    private static final class TestPlayer extends VoidPlayer {
        ArrayDeque<Runnable> tasks;

        TestPlayer() {
            super(null);
        }

        @Override
        public void runSafely(Runnable task) {
            tasks.add(task);
        }
    }
}
