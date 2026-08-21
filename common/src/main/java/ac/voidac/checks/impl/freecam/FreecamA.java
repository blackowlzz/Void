package ac.voidac.checks.impl.freecam;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.manager.SetbackTeleportUtil;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.data.SetBackData;
import ac.voidac.utils.math.Location;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;

import java.util.Random;

/**
 * Detects Freecam by sending a small horizontal teleport probe after extended
 * position staleness. Legitimate AFK players accept the probe and remain at
 * the new position. Freecam clients accept the teleport but immediately revert
 * to their frozen (pre-probe) position on the next tick, which this check detects.
 *
 * This approach eliminates false positives on AFK/stationary players that plagued
 * the old position-staleness-only detection.
 */
@CheckData(name = "FreecamA", stableKey = "void.freecam.teleport_probe",
        description = "Teleport probe detects Freecam overriding server position",
        decay = 0, setback = -1)
public class FreecamA extends Check implements PacketCheck {

    private enum State { MONITORING, PROBE_SENT, VERIFYING }

    private final Random random = new Random();

    private State state = State.MONITORING;

    // Reference position tracking
    private double refX = Double.NaN, refY = Double.NaN, refZ = Double.NaN;
    private long positionFrozenSince = -1;
    private long joinTime;
    private boolean initialized = false;

    // Probe data
    private double originalX;
    private double probeX;
    private long probeSentTime;
    private long lastProbeTime = 0;
    private int verifyPacketsRemaining;

    // 30 seconds of zero movement before probing
    private static final long FREEZE_THRESHOLD_MS = 15_000;
    // 1/32 block horizontal offset: imperceptible, won't clip into walls
    private static final double PROBE_OFFSET = 0.03125;
    // Timeout waiting for probe acceptance
    private static final long PROBE_TIMEOUT_MS = 5_000;
    // Cooldown between probes after confirming AFK
    private static final long PROBE_COOLDOWN_MS = 120_000;
    // Number of position packets to check after probe acceptance
    private static final int VERIFY_PACKETS = 5;

    public FreecamA(VoidPlayer player) {
        super(player);
        this.joinTime = System.currentTimeMillis();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        long now = System.currentTimeMillis();

        // Don't check in the first 10 seconds after join
        if (now - joinTime < 10_000) return;

        // Exempt spectator, vehicles, bed, dead, creative flight
        if (player.gamemode == GameMode.SPECTATOR
                || player.inVehicle()
                || player.isInBed
                || player.compensatedEntities.self.isDead
                || player.isFlying) {
            resetAll();
            return;
        }

        // Handle teleport responses
        if (player.packetStateData.lastPacketWasTeleport) {
            if (state == State.PROBE_SENT) {
                // Check if this teleport acceptance matches our probe position
                if (Math.abs(player.x - probeX) < 0.01) {
                    // Our probe was accepted, now verify subsequent packets
                    state = State.VERIFYING;
                    verifyPacketsRemaining = VERIFY_PACKETS;
                } else {
                    // A different teleport was accepted (setback, etc.), reset
                    resetAll();
                }
            } else {
                // Teleport while not probing: reset position tracking
                resetAll();
            }
            return;
        }

        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        if (!flying.hasPositionChanged()) return;

        double px = flying.getLocation().getPosition().getX();
        double py = flying.getLocation().getPosition().getY();
        double pz = flying.getLocation().getPosition().getZ();

        switch (state) {
            case MONITORING:
                handleMonitoring(px, py, pz, now);
                break;
            case PROBE_SENT:
                // Still waiting for teleport acceptance
                if (now - probeSentTime > PROBE_TIMEOUT_MS) {
                    resetAll();
                }
                break;
            case VERIFYING:
                handleVerify(px, pz, now);
                break;
        }
    }

    private void handleMonitoring(double px, double py, double pz, long now) {
        if (!initialized) {
            refX = px;
            refY = py;
            refZ = pz;
            positionFrozenSince = now;
            initialized = true;
            return;
        }

        // Player actually moved: reset
        if (px != refX || py != refY || pz != refZ) {
            refX = px;
            refY = py;
            refZ = pz;
            positionFrozenSince = now;
            reward();
            return;
        }

        // Position still frozen: check if we should probe
        long frozenMs = now - positionFrozenSince;
        if (frozenMs >= FREEZE_THRESHOLD_MS && now - lastProbeTime >= PROBE_COOLDOWN_MS) {
            sendProbe(now);
        }
    }

    private void sendProbe(long now) {
        SetbackTeleportUtil setbackUtil = player.getSetbackTeleportUtil();
        SetBackData currentSetback = setbackUtil.getRequiredSetBack();

        // Don't interfere with an in-progress setback
        if (currentSetback != null && !currentSetback.isComplete()) return;
        if (setbackUtil.lastKnownGoodPosition == null) return;

        originalX = refX;
        probeX = refX + PROBE_OFFSET;

        // Bracket the teleport with transactions for proper sequencing
        player.sendTransaction();

        int teleportId = random.nextInt() | Integer.MIN_VALUE;
        int transaction = player.lastTransactionSent.get();

        // Register in the setback system so checkTeleportQueue recognises it
        // plugin=true allows real anticheat setbacks to override if needed
        setbackUtil.addSentTeleport(
                new Location(null, probeX, refY, refZ, player.yaw % 360, player.pitch % 360),
                null, transaction,
                RelativeFlag.YAW.or(RelativeFlag.PITCH),
                true, teleportId
        );

        // Send the actual teleport packet
        PacketEvents.getAPI().getProtocolManager().sendPacketSilently(
                player.user.getChannel(),
                new WrapperPlayServerPlayerPositionAndLook(
                        probeX, refY, refZ, 0, 0,
                        RelativeFlag.YAW.or(RelativeFlag.PITCH).getMask(),
                        teleportId, false
                )
        );

        player.sendTransaction();

        state = State.PROBE_SENT;
        probeSentTime = now;
        lastProbeTime = now;
    }

    private void handleVerify(double px, double pz, long now) {
        verifyPacketsRemaining--;

        boolean atOriginal = Math.abs(px - originalX) < 0.001;
        boolean atProbe = Math.abs(px - probeX) < 0.001;

        if (atOriginal && !atProbe) {
            // Position reverted to pre-probe coordinates, Freecam detected
            flagAndAlert("probe_reverted"
                    + " expected=" + String.format("%.4f", probeX)
                    + " got=" + String.format("%.4f", px));
            // Allow re-probe in 30 seconds for repeated detection
            lastProbeTime = now - PROBE_COOLDOWN_MS + 30_000;
            refX = px;
            refZ = pz;
            positionFrozenSince = now;
            state = State.MONITORING;
            return;
        }

        if (atProbe || verifyPacketsRemaining <= 0) {
            // Position matches probe: legitimate AFK player
            refX = px;
            refY = player.y;
            refZ = pz;
            positionFrozenSince = now;
            state = State.MONITORING;
            reward();
        }
    }

    private void resetAll() {
        initialized = false;
        positionFrozenSince = System.currentTimeMillis();
        state = State.MONITORING;
    }
}
