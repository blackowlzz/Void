package ac.voidac.predictionengine;

import ac.voidac.checks.impl.velocity.ExplosionHandler;
import ac.voidac.checks.impl.velocity.KnockbackHandler;
import ac.voidac.manager.CheckManager;
import ac.voidac.manager.LastInstanceManager;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.data.LastInstance;
import ac.voidac.utils.data.VelocityData;
import ac.voidac.utils.data.packetentity.PacketEntitySelf;
import ac.voidac.utils.data.tags.SyncedTags;
import ac.voidac.utils.latency.CompensatedEntities;
import ac.voidac.utils.math.Vector3dm;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.attribute.Attribute;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PointThreeEstimatorVelocityTest {
    private static final int ENTITY_ID = 7;
    private static final int VELOCITY_TRANSACTION = 5;
    private static final int RECEIVED_TRANSACTION = 6;

    @BeforeAll
    static void initializeProtocol() {
        System.setProperty("VoidPlatformOverride", "BUKKIT");
        PacketEvents.setAPI(new PacketEventsAPI<>() {
            public boolean isLoaded() { return true; }
            public void init() { }
            public boolean isInitialized() { return true; }
            public boolean isTerminated() { return false; }
            public Object getPlugin() { return null; }
            public ServerManager getServerManager() { return () -> ServerVersion.V_26_2; }
            public ProtocolManager getProtocolManager() { return null; }
            public PlayerManager getPlayerManager() { return null; }
            public NettyManager getNettyManager() { return new NettyManagerImpl(); }
            public ChannelInjector getInjector() { return null; }
        });
    }

    @Test
    void blockChangeKeepsKnockbackAnIdlePlayerAlreadyTook() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.queueKnockback();
        fixture.player.likelyKB = fixture.knockback.calculateRequiredKB(ENTITY_ID, RECEIVED_TRANSACTION, false);
        VelocityData taken = fixture.player.likelyKB;
        assertNotNull(taken);

        fixture.changeBlockUnderFeet();

        assertSame(taken, fixture.player.likelyKB);
    }

    @Test
    void blockChangeKeepsExplosionAnIdlePlayerAlreadyTook() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.queueExplosion();
        fixture.player.likelyExplosions = fixture.explosion.getPossibleExplosions(RECEIVED_TRANSACTION, false);
        VelocityData taken = fixture.player.likelyExplosions;
        assertNotNull(taken);

        fixture.changeBlockUnderFeet();

        assertSame(taken, fixture.player.likelyExplosions);
    }

    @Test
    void blockChangeRequiresKnockbackThatArrivedDuringAnIdleTick() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.queueKnockback();

        fixture.changeBlockUnderFeet();

        assertNotNull(fixture.player.likelyKB);
    }

    @Test
    void blockChangeLeavesVelocityUntouchedWhenThePlayerCouldNotSkipATick() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.queueKnockback();

        fixture.changeBlockUnderFeet();

        assertNull(fixture.player.likelyKB);
    }

    static class Fixture {
        final TestPlayer player = allocate(TestPlayer.class);
        final KnockbackHandler knockback;
        final ExplosionHandler explosion;

        Fixture(boolean idle) throws Exception {
            player.idle = idle;
            player.entityID = ENTITY_ID;
            player.x = 0.5;
            player.z = 0.5;
            set(player, "lastTransactionReceived", new AtomicInteger(RECEIVED_TRANSACTION));

            CompensatedEntities entities = allocate(CompensatedEntities.class);
            entities.self = allocate(TestSelf.class);
            set(player, "compensatedEntities", entities);
            set(player, "tagManager", new SyncedTags(player));
            set(player, "pointThreeEstimator", new PointThreeEstimator(player));
            set(player, "lastInstanceManager", new TestLastInstanceManager(player));
            UncertaintyHandler uncertainty = allocate(UncertaintyHandler.class);
            set(uncertainty, "lastPointThree", new LastInstance(player));
            set(player, "uncertaintyHandler", uncertainty);

            knockback = new TestKnockbackHandler(player);
            explosion = new TestExplosionHandler(player);
            TestChecks checks = allocate(TestChecks.class);
            checks.knockback = knockback;
            checks.explosion = explosion;
            set(player, "checkManager", checks);
        }

        void queueKnockback() throws Exception {
            firstBread(knockback).add(new VelocityData(ENTITY_ID, VELOCITY_TRANSACTION, false, new Vector3dm(0, 0.36, -0.4)));
        }

        void queueExplosion() throws Exception {
            firstBread(explosion).add(new VelocityData(-1, VELOCITY_TRANSACTION, false, new Vector3dm(0.8, 0.5, 0)));
        }

        void changeBlockUnderFeet() {
            player.pointThreeEstimator.handleChangeBlock(0, -1, 0,
                    WrappedBlockState.getDefaultState(ClientVersion.V_26_2, StateTypes.STONE));
        }

        @SuppressWarnings("unchecked")
        private static Deque<VelocityData> firstBread(Object handler) throws Exception {
            Field field = handler.getClass().getSuperclass().getDeclaredField("firstBreadMap");
            field.setAccessible(true);
            return (Deque<VelocityData>) field.get(handler);
        }
    }

    static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static class TestPlayer extends VoidPlayer {
        boolean idle;
        TestPlayer() { super(null); }
        @Override public ClientVersion getClientVersion() { return ClientVersion.V_26_2; }
        @Override public boolean inVehicle() { return false; }
        @Override public void updateVelocityMovementSkipping() { couldSkipTick = idle; }
    }

    static class TestSelf extends PacketEntitySelf {
        TestSelf() { super(null); }
        @Override public double getAttributeValue(Attribute attribute) { return 1; }
    }

    static class TestChecks extends CheckManager {
        KnockbackHandler knockback;
        ExplosionHandler explosion;
        TestChecks() { super(null); }
        @Override public KnockbackHandler getKnockbackHandler() { return knockback; }
        @Override public ExplosionHandler getExplosionHandler() { return explosion; }
    }

    static class TestLastInstanceManager extends LastInstanceManager {
        TestLastInstanceManager(VoidPlayer player) { super(player); }
        @Override public void reload() { }
    }

    static class TestKnockbackHandler extends KnockbackHandler {
        TestKnockbackHandler(VoidPlayer player) { super(player); }
        @Override public void reload() { }
    }

    static class TestExplosionHandler extends ExplosionHandler {
        TestExplosionHandler(VoidPlayer player) { super(player); }
        @Override public void reload() { }
    }
}
