package ac.voidac.checks.impl.combat;

import ac.voidac.api.AbstractCheck;
import ac.voidac.checks.Check;
import ac.voidac.manager.CheckManager;
import ac.voidac.manager.PunishmentManager;
import ac.voidac.manager.SetbackTeleportUtil;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.voidac.utils.data.PacketStateData;
import ac.voidac.utils.data.packetentity.PacketEntity;
import ac.voidac.utils.data.packetentity.PacketEntitySelf;
import ac.voidac.utils.latency.CompensatedEntities;
import ac.voidac.utils.latency.CompensatedInventory;
import ac.voidac.utils.latency.CompensatedWorld;
import ac.voidac.utils.math.TrigHandler;
import ac.voidac.utils.math.Vector3dm;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.attribute.Attribute;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachWallHitTest {
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
    void detectsSurvivalAttackThroughStoneWithinReach() throws Exception {
        assertEquals("WALL_HIT", check(true, GameMode.SURVIVAL));
    }

    @Test
    void detectsCreativeAttackThroughStoneWithinReach() throws Exception {
        assertEquals("WALL_HIT", check(true, GameMode.CREATIVE));
    }

    @Test
    void acceptsUnobstructedAttackWithinReach() throws Exception {
        assertEquals("NONE", check(false, GameMode.SURVIVAL));
    }

    @Test
    void acceptsAnAttackWhenOnePossibleEyeHeightClearsTheWall() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.world.minimumWallY = 1;
        assertEquals("NONE", fixture.result());
    }

    @Test
    void acceptsAnAttackAboveASlab() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.player.eyes = new double[]{1.62};
        fixture.world.wall = WrappedBlockState.getDefaultState(ClientVersion.V_26_2, StateTypes.STONE_SLAB);
        assertEquals("NONE", fixture.result());
    }

    @Test
    void ignoresBlocksBehindTheTarget() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.world.wallZ = 4;
        assertEquals("NONE", fixture.result());
    }

    @Test
    void acceptsAnAttackWhenAnAlternateRotationClearsTheWall() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.player.x = 0.9;
        fixture.player.lastYaw = -20;
        fixture.targetBox.maxX = 2;
        fixture.world.maximumWallX = 0;
        assertEquals("NONE", fixture.result());
    }

    @Test
    void doesNotFlagWallsDuringRotationPredictionOrRightClickInteraction() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        assertEquals("NONE", fixture.result(true, true));
        assertEquals("NONE", fixture.result(false, false));
    }

    @Test
    void honorsDisabledWallHitAndHardEntityUncertainty() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.checks.wallHit.setEnabled(false);
        assertEquals("NONE", fixture.result());
        fixture.checks.wallHit.setEnabled(true);
        fixture.world.hardEntity = true;
        assertEquals("NONE", fixture.result());
    }

    @Test
    void exemptsRecentBlockChangesForTwoMovementTicks() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.checks.wallHit.handleBlockChange(0, 1, 1);
        assertEquals("NONE", fixture.result());
        fixture.checks.wallHit.advanceTick();
        assertEquals("NONE", fixture.result());
        fixture.checks.wallHit.advanceTick();
        assertEquals("WALL_HIT", fixture.result());
    }

    @Test
    void expiresBlockUncertaintyThroughTheReachPacketListener() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.checks.wallHit.handleBlockChange(0, 1, 1);
        PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
        set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
        fixture.reach.onPacketReceive(flying);
        fixture.reach.onPacketReceive(flying);
        assertEquals("WALL_HIT", fixture.result());
    }

    @Test
    void ignoresWallsOutsideTheClientsBlockInteractionRange() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.self.blockRange = 0.25;
        assertEquals("NONE", fixture.result());
    }

    @Test
    void onlyCancelsWhenTheEntireTargetIsCoveredRegardlessOfRotation() {
        Vector3dm eyes = new Vector3dm(0.5, 1.62, 0.5);
        SimpleCollisionBox target = new SimpleCollisionBox(0.2, 0, 2.5, 0.8, 1.8, 3.1);
        assertTrue(WallHit.coversTarget(new SimpleCollisionBox(0, -1, 1, 1, 3, 2), eyes, target));
        assertFalse(WallHit.coversTarget(new SimpleCollisionBox(0, 1.5, 1, 1, 2, 2), eyes, target));
        assertFalse(WallHit.coversTarget(new SimpleCollisionBox(0, -1, 4, 1, 3, 5), eyes, target));
    }

    @Test
    void cancelsTheFirstHitThroughAFullWallWithoutDependingOnRotation() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        set(fixture.checks.wallHit, "blockImpossibleHits", true);
        assertTrue(fixture.checks.wallHit.shouldCancel(fixture.targetBox, fixture.player.x, 0, fixture.player.z));
        fixture.player.noModifyPacketPermission = true;
        assertFalse(fixture.checks.wallHit.shouldCancel(fixture.targetBox, fixture.player.x, 0, fixture.player.z));
    }

    @Test
    void doesNotCancelTargetsPartiallyVisibleAroundAWall() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        set(fixture.checks.wallHit, "blockImpossibleHits", true);
        fixture.world.maximumWallX = 0;
        fixture.targetBox.maxX = 4;
        assertFalse(fixture.checks.wallHit.shouldCancel(fixture.targetBox, fixture.player.x, 0, fixture.player.z));
    }

    @Test
    void modernAttackPacketIsCancelledAndProducesWallHitAlertInBothGameModes() throws Exception {
        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE}) {
            Fixture fixture = new Fixture(true, mode);
            set(fixture.checks.wallHit, "blockImpossibleHits", true);
            PacketReceiveEvent attack = fixture.attackPacket();
            fixture.reach.onPacketReceive(attack);
            assertTrue(attack.isCancelled(), mode.name());
            PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
            set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
            fixture.reach.onPacketReceive(flying);
            assertEquals(1, fixture.punishments.alerts, mode.name());
            assertEquals(fixture.checks.wallHit, fixture.punishments.flagged);
            assertTrue(fixture.punishments.verbose.contains("precheck=BLOCK"));
            assertTrue(fixture.punishments.verbose.contains("cancel_requested=true"));
        }
    }

    @Test
    void modernAttackPacketDoesNotCancelOrAlertWithoutAWall() throws Exception {
        Fixture fixture = new Fixture(false, GameMode.SURVIVAL);
        set(fixture.checks.wallHit, "blockImpossibleHits", true);
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
        PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
        set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
        fixture.reach.onPacketReceive(flying);
        assertEquals(0, fixture.punishments.alerts);
    }

    @Test
    void cancelsAttacksAcrossAdjacentWallBlocks() throws Exception {
        for (double x : new double[]{0.85, 1.0, 1.15}) {
            for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE}) {
                Fixture fixture = new Fixture(true, mode);
                fixture.player.x = x;
                fixture.targetBox.minX = x - 0.3;
                fixture.targetBox.maxX = x + 0.3;
                set(fixture.checks.wallHit, "blockImpossibleHits", true);
                PacketReceiveEvent attack = fixture.attackPacket();
                fixture.reach.onPacketReceive(attack);
                assertTrue(attack.isCancelled(), mode + " x=" + x);
                PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
                set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
                fixture.reach.onPacketReceive(flying);
                assertEquals(1, fixture.punishments.alerts);
            }
        }
    }

    @Test
    void cancelsAcrossEastWestWallsAndHorizontalFloors() throws Exception {
        Fixture east = new Fixture(true, GameMode.SURVIVAL);
        east.world.wallAxis = 0;
        east.player.z = 1;
        east.targetBox = new SimpleCollisionBox(2.5, 0, 0.7, 3.1, 1.8, 1.3);
        set(east.checks.wallHit, "blockImpossibleHits", true);
        PacketReceiveEvent attack = east.attackPacket();
        east.reach.onPacketReceive(attack);
        assertTrue(attack.isCancelled());

        Fixture floor = new Fixture(true, GameMode.SURVIVAL);
        floor.world.wallAxis = 1;
        floor.world.wallZ = -1;
        floor.player.x = 1;
        floor.player.z = 1;
        floor.targetBox = new SimpleCollisionBox(0.7, -3, 0.7, 1.3, -1.2, 1.3);
        set(floor.checks.wallHit, "blockImpossibleHits", true);
        attack = floor.attackPacket();
        floor.reach.onPacketReceive(attack);
        assertTrue(attack.isCancelled());
    }

    @Test
    void doesNotFillHolesOrRecentlyChangedBlocksWhenMergingWalls() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.player.x = 1;
        fixture.targetBox.minX = 0.7;
        fixture.targetBox.maxX = 1.3;
        fixture.world.hole = true;
        set(fixture.checks.wallHit, "blockImpossibleHits", true);
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
        fixture.world.hole = false;
        fixture.checks.wallHit.handleBlockChange(1, 1, 1);
        attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
    }

    @Test
    void cancellationToggleAndPermissionStillAllowAlerts() throws Exception {
        for (boolean permission : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
            set(fixture.checks.wallHit, "blockImpossibleHits", permission);
            fixture.player.noModifyPacketPermission = permission;
            PacketReceiveEvent attack = fixture.attackPacket();
            fixture.reach.onPacketReceive(attack);
            assertFalse(attack.isCancelled());
            PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
            set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
            fixture.reach.onPacketReceive(flying);
            assertEquals(1, fixture.punishments.alerts);
            assertTrue(fixture.punishments.verbose.contains(permission
                    ? "precheck=PACKET_MODIFICATION_DISABLED" : "precheck=CONFIG_DISABLED"));
            assertTrue(fixture.punishments.verbose.contains("cancel_requested=false"));
        }
    }

    @Test
    void blocksRepeatedConfirmedWallHitsWithIncompleteCoverage() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.world.maximumWallX = 0;
        fixture.targetBox.maxX = 4;
        set(fixture.checks.wallHit, "blockImpossibleHits", true);
        PacketReceiveEvent first = fixture.attackPacket();
        fixture.reach.onPacketReceive(first);
        assertFalse(first.isCancelled());
        PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
        set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
        fixture.reach.onPacketReceive(flying);
        assertEquals(1, fixture.punishments.alerts);
        PacketReceiveEvent second = fixture.attackPacket();
        fixture.reach.onPacketReceive(second);
        assertTrue(second.isCancelled());
        fixture.reach.onPacketReceive(flying);
        assertEquals(2, fixture.punishments.alerts);
        assertTrue(fixture.punishments.verbose.contains("precheck=CONFIRMED_RAY"));
    }

    @Test
    void cancelsTargetCoveredByTheFrontWallAndRoofTogether() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.player.eyes = new double[]{1.62};
        fixture.player.pitch = 55;
        fixture.player.lastPitch = 55;
        fixture.targetBox = new SimpleCollisionBox(0.2, -2, 2.2, 0.8, -0.2, 2.8);
        fixture.world.shape = (x, y, z) -> (z == 1 && y >= -2 && y <= 0)
                || (y == 0 && z == 2);
        set(fixture.checks.wallHit, "blockImpossibleHits", true);
        assertEquals("WALL_HIT", fixture.result());
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertTrue(attack.isCancelled());
    }

    @Test
    void confirmedAttackCancellationExpiresAndDoesNotTransferTargets() throws Exception {
        Fixture fixture = confirmedIncompleteFixture();
        assertFalse(fixture.checks.wallHit.canCancelConfirmedAttack(43));
        for (int tick = 0; tick < 40; tick++) fixture.checks.wallHit.advanceTick();
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
        fixture.checks.wallHit.confirmObstruction(42);
        set(fixture.checks.wallHit, "confirmationDeadline", System.nanoTime() - 1);
        assertFalse(fixture.checks.wallHit.canCancelConfirmedAttack(42));
    }

    @Test
    void confirmedAttackCancellationRespectsPermissionsAndNewBlockChanges() throws Exception {
        Fixture fixture = confirmedIncompleteFixture();
        fixture.player.noModifyPacketPermission = true;
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
        fixture.player.noModifyPacketPermission = false;
        fixture.checks.wallHit.handleBlockChange(0, 1, 1);
        attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
    }

    @Test
    void confirmedAttacksCancelInCreativeAndRespectTheConfigToggle() throws Exception {
        Fixture fixture = confirmedIncompleteFixture();
        fixture.player.gamemode = GameMode.CREATIVE;
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertTrue(attack.isCancelled());
        set(fixture.checks.wallHit, "blockImpossibleHits", false);
        attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
    }

    @Test
    void confirmationSurvivesANormalAttackCooldown() throws Exception {
        Fixture fixture = confirmedIncompleteFixture();
        for (int tick = 0; tick < 20; tick++) fixture.checks.wallHit.advanceTick();
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertTrue(attack.isCancelled());
    }

    @Test
    void detectsModernAttackAtReportedCornerCoordinates() throws Exception {
        Fixture fixture = reportedCornerFixture();
        assertEquals("WALL_HIT", fixture.result());
    }

    @Test
    void preservesLegacyToleranceAtTheSameCorner() throws Exception {
        Fixture fixture = reportedCornerFixture();
        fixture.player.version = ClientVersion.V_1_8;
        assertEquals("NONE", fixture.result());
    }

    @Test
    void reportedCornerAttackFlagsAndThenCancels() throws Exception {
        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE}) {
            Fixture fixture = reportedCornerFixture();
            fixture.player.gamemode = mode;
            set(fixture.checks.wallHit, "blockImpossibleHits", true);
            fixture.reach.onPacketReceive(fixture.attackPacket());
            PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
            set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
            fixture.reach.onPacketReceive(flying);
            assertEquals(1, fixture.punishments.alerts, mode.name());
            PacketReceiveEvent second = fixture.attackPacket();
            fixture.reach.onPacketReceive(second);
            assertTrue(second.isCancelled(), mode.name());
        }
    }

    @Test
    void modernClientsKeepTheirSmallEdgeTolerance() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.player.x = 0.9999;
        fixture.targetBox.maxX = 1.3;
        fixture.world.maximumWallX = 0;
        assertEquals("NONE", fixture.result());
    }

    private Fixture reportedCornerFixture() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.player.x = 43.700;
        fixture.player.y = 62;
        fixture.player.z = -290.670;
        fixture.player.yaw = -137.2f;
        fixture.player.lastYaw = -137.2f;
        fixture.player.pitch = 21.5f;
        fixture.player.lastPitch = 21.5f;
        fixture.player.boundingBox = new SimpleCollisionBox(43.4, 62, -290.97, 44, 63.8, -290.37);
        fixture.targetBox = new SimpleCollisionBox(44.4, 62, -292, 45, 63.8, -291.4);
        fixture.world.shape = (x, y, z) -> y >= 62 && y <= 64
                && (x == 44 && z == -291 || x == 43 && z == -292);
        return fixture;
    }

    @Test
    void confirmedWallHitIsCancelledEvenWhenTheTargetCenterIsClear() throws Exception {
        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE}) {
            Fixture fixture = confirmedIncompleteFixture();
            fixture.player.gamemode = mode;
            fixture.player.x = 0.9;
            assertEquals(WallHit.CancellationDecision.NO_OBSTRUCTION,
                    fixture.checks.wallHit.cancellationDecision(fixture.targetBox, 0.9, 0, 0.5));
            assertEquals("WALL_HIT", fixture.result());
            PacketReceiveEvent attack = fixture.attackPacket();
            fixture.reach.onPacketReceive(attack);
            assertTrue(attack.isCancelled(), mode.name());
            PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
            set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
            fixture.reach.onPacketReceive(flying);
            assertTrue(fixture.punishments.verbose.contains("precheck=CONFIRMED_RAY"));
            assertTrue(fixture.punishments.verbose.contains("cancel_requested=true"));
        }
    }

    @Test
    void clearCenterDoesNotCancelWithoutARecentConfirmation() throws Exception {
        Fixture fixture = confirmedIncompleteFixture();
        fixture.player.x = 0.9;
        fixture.checks.wallHit.clearConfirmation(42);
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
    }

    @Test
    void clearAlternateRayStillPreventsConfirmedCancellation() throws Exception {
        Fixture fixture = confirmedIncompleteFixture();
        fixture.player.x = 0.9;
        fixture.player.lastYaw = -25;
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
        assertFalse(fixture.checks.wallHit.canCancelConfirmedAttack(42));
    }

    @Test
    void confirmedAttackCancellationRechecksTheCurrentView() throws Exception {
        Fixture fixture = confirmedIncompleteFixture();
        fixture.player.x = 0.9;
        fixture.player.yaw = -25;
        fixture.player.lastYaw = -25;
        PacketReceiveEvent attack = fixture.attackPacket();
        fixture.reach.onPacketReceive(attack);
        assertFalse(attack.isCancelled());
        assertFalse(fixture.checks.wallHit.canCancelConfirmedAttack(42));
    }

    private Fixture confirmedIncompleteFixture() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.world.maximumWallX = 0;
        fixture.targetBox.maxX = 4;
        set(fixture.checks.wallHit, "blockImpossibleHits", true);
        fixture.reach.onPacketReceive(fixture.attackPacket());
        PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
        set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
        fixture.reach.onPacketReceive(flying);
        assertEquals(1, fixture.punishments.alerts);
        return fixture;
    }

    @Test
    void legacyAttackPacketsAlsoCancelAndAlert() throws Exception {
        for (ClientVersion version : new ClientVersion[]{ClientVersion.V_1_8, ClientVersion.V_1_9,
                ClientVersion.V_1_20_5, ClientVersion.V_1_21_11}) {
            Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
            fixture.player.version = version;
            set(fixture.checks.wallHit, "blockImpossibleHits", true);
            PacketReceiveEvent attack = fixture.attackPacket();
            set(attack, "packetType", PacketType.Play.Client.INTERACT_ENTITY);
            WrapperPlayClientInteractEntity wrapper = allocate(WrapperPlayClientInteractEntity.class);
            wrapper.setEntityId(42);
            wrapper.setAction(WrapperPlayClientInteractEntity.InteractAction.ATTACK);
            attack.setLastUsedWrapper(wrapper);
            fixture.reach.onPacketReceive(attack);
            assertTrue(attack.isCancelled(), version.name());
            PacketReceiveEvent flying = allocate(PacketReceiveEvent.class);
            set(flying, "packetType", PacketType.Play.Client.PLAYER_FLYING);
            fixture.reach.onPacketReceive(flying);
            assertEquals(1, fixture.punishments.alerts, version.name());
        }
    }

    @Test
    void detectsWallsAtNegativeCoordinatesAndBelowZero() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.player.x = -185.5;
        fixture.player.y = -60;
        fixture.player.z = -80.5;
        fixture.world.wallZ = -80;
        fixture.targetBox = new SimpleCollisionBox(-185.8, -60, -78.5, -185.2, -58.2, -77.9);
        assertEquals("WALL_HIT", fixture.result());
    }

    @Test
    void doesNotDoubleAdvanceUncertaintyOnTickEndAfterMovement() throws Exception {
        Fixture fixture = new Fixture(true, GameMode.SURVIVAL);
        fixture.checks.wallHit.handleBlockChange(0, 1, 1);
        PacketReceiveEvent event = allocate(PacketReceiveEvent.class);
        set(event, "packetType", PacketType.Play.Client.PLAYER_FLYING);
        fixture.reach.onPacketReceive(event);
        fixture.player.packetStateData.didSendMovementBeforeTickEnd = true;
        set(event, "packetType", PacketType.Play.Client.CLIENT_TICK_END);
        fixture.reach.onPacketReceive(event);
        assertEquals("NONE", fixture.result());
        fixture.player.packetStateData.didSendMovementBeforeTickEnd = false;
        fixture.reach.onPacketReceive(event);
        assertEquals("WALL_HIT", fixture.result());
    }

    private String check(boolean obstructed, GameMode mode) throws Exception {
        return new Fixture(obstructed, mode).result();
    }

    static class Fixture {
        TestPlayer player = allocate(TestPlayer.class);
        TestWorld world;
        TestChecks checks = allocate(TestChecks.class);
        TestSelf self = allocate(TestSelf.class);
        SimpleCollisionBox targetBox = new SimpleCollisionBox(0.2, 0, 2.5, 0.8, 1.8, 3.1);
        Reach reach;
        PacketEntity target;
        TestPunishments punishments;

        Fixture(boolean obstructed, GameMode mode) throws Exception {
            player.eyes = new double[]{1.62, 1.27, 0.4};
            player.version = ClientVersion.V_26_2;
            player.gamemode = mode;
            player.x = 0.5;
            player.z = 0.5;
            player.boundingBox = new SimpleCollisionBox(0.2, 0, 0.2, 0.8, 1.8, 0.8);
            set(player, "packetStateData", new PacketStateData());
            set(player, "trigHandler", new TrigHandler(player));
            world = new TestWorld(player, obstructed);
            set(player, "compensatedWorld", world);
            self.blockRange = 4.5;
            CompensatedEntities entities = allocate(CompensatedEntities.class);
            entities.self = self;
            set(entities, "entityMap", new Int2ObjectOpenHashMap<PacketEntity>());
            set(player, "compensatedEntities", entities);
            checks.wallHit = new TestWallHit(player);
            checks.wallHit.setEnabled(true);
            set(player, "checkManager", checks);
            reach = new TestReach(player);
            target = new PacketEntity(player, EntityTypes.PLAYER) {
                @Override
                public SimpleCollisionBox getPossibleCollisionBoxes() {
                    return targetBox.copy();
                }
            };
            entities.entityMap.put(42, target);
            player.setback = allocate(TestSetback.class);
            set(player, "inventory", allocate(TestInventory.class));
            punishments = new TestPunishments(player);
            set(player, "punishmentManager", punishments);
        }

        String result() throws Exception { return result(false, true); }

        String result(boolean prediction, boolean attack) throws Exception {
            var method = Reach.class.getDeclaredMethod("checkReach", PacketEntity.class,
                    double.class, double.class, double.class, boolean.class, float.class, float.class, boolean.class, boolean.class);
            method.setAccessible(true);
            Object result = method.invoke(reach, target, player.x, player.y, player.z, true, 3f, 0f, prediction, attack);
            var type = result.getClass().getDeclaredMethod("type");
            type.setAccessible(true);
            return type.invoke(result).toString();
        }

        PacketReceiveEvent attackPacket() throws Exception {
            PacketReceiveEvent event = allocate(PacketReceiveEvent.class);
            User user = allocate(User.class);
            user.setClientVersion(player.version);
            set(event, "user", user);
            set(event, "serverVersion", ServerVersion.V_26_2);
            set(event, "packetType", PacketType.Play.Client.ATTACK);
            WrapperPlayClientAttack wrapper = allocate(WrapperPlayClientAttack.class);
            wrapper.setEntityId(42);
            event.setLastUsedWrapper(wrapper);
            return event;
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
        double[] eyes;
        ClientVersion version;
        SetbackTeleportUtil setback;
        TestPlayer() { super(null); }
        @Override public ClientVersion getClientVersion() { return version; }
        @Override public double[] getPossibleEyeHeights() { return eyes; }
        @Override public boolean canSkipTicks() { return false; }
        @Override public SetbackTeleportUtil getSetbackTeleportUtil() { return setback; }
        @Override public void onPacketCancel() { }
    }

    static class TestReach extends Reach {
        TestReach(VoidPlayer player) { super(player); }
        @Override public void reload() { }
    }

    static class TestWallHit extends WallHit {
        TestWallHit(VoidPlayer player) { super(player); }
        @Override public void reload() { }
    }

    static class TestChecks extends CheckManager {
        WallHit wallHit;
        TestChecks() { super(null); }
        @Override public <T extends AbstractCheck> T getCheck(Class<T> type) { return type.cast(wallHit); }
    }

    static class TestWorld extends CompensatedWorld {
        BlockShape shape;
        private final boolean obstructed;
        int minimumWallY = -100;
        int maximumWallX = 100;
        int wallZ = 1;
        int wallAxis = 2;
        boolean hole;
        boolean hardEntity;
        WrappedBlockState wall = WrappedBlockState.getDefaultState(ClientVersion.V_26_2, StateTypes.STONE);
        TestWorld(VoidPlayer player, boolean obstructed) {
            super(player);
            this.obstructed = obstructed;
        }
        @Override public WrappedBlockState getBlock(int x, int y, int z) {
            if (shape != null) return shape.solid(x, y, z)
                    ? wall : WrappedBlockState.getDefaultState(ClientVersion.V_26_2, StateTypes.AIR);
            int coordinate = wallAxis == 0 ? x : wallAxis == 1 ? y : z;
            return obstructed && coordinate == wallZ && y >= minimumWallY && x <= maximumWallX
                    && !(hole && x == 1 && y == 1)
                    ? wall : WrappedBlockState.getDefaultState(ClientVersion.V_26_2, StateTypes.AIR);
        }
        @Override public boolean isNearHardEntity(SimpleCollisionBox box) { return hardEntity; }
    }

    interface BlockShape {
        boolean solid(int x, int y, int z);
    }

    static class TestSelf extends PacketEntitySelf {
        double blockRange;
        TestSelf() { super(null); }
        @Override public double getAttributeValue(Attribute attribute) {
            return attribute == Attributes.BLOCK_INTERACTION_RANGE ? blockRange : 3;
        }
    }

    static class TestInventory extends CompensatedInventory {
        TestInventory() { super(null); }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return ItemStack.EMPTY; }
        @Override public ItemStack getStartOfTickStack() { return ItemStack.EMPTY; }
    }

    static class TestSetback extends SetbackTeleportUtil {
        TestSetback() { super(null); }
        @Override public boolean shouldBlockMovement() { return false; }
    }

    static class TestPunishments extends PunishmentManager {
        int alerts;
        Check flagged;
        String verbose = "";
        TestPunishments(VoidPlayer player) { super(player); }
        @Override public void handleViolation(Check check) { flagged = check; }
        @Override public void handleAutoPunish(Check check) { }
        @Override public boolean handleAlert(VoidPlayer player, String verbose, Check check) {
            this.verbose = verbose;
            alerts++;
            return true;
        }
    }
}
