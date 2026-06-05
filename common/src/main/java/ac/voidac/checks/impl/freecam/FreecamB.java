package ac.voidac.checks.impl.freecam;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

/**
 * Detects Freecam implementations that fully suppress outgoing movement packets
 * while the player remains responsive (transactions, keepalives, tick-end).
 *
 * Note on Meteor Client specifically: Meteor's Freecam does NOT suppress movement
 * packets. It is a pure rendering exploit — the player entity stays frozen at the
 * activation point (indistinguishable from AFK) while only the camera moves
 * client-side. Meteor accepts server teleports normally. This check does NOT
 * detect Meteor's Freecam. FreecamA (teleport probe) also does not detect it
 * for the same reason.
 *
 * This check targets simpler Freecam implementations that stop sending
 * WrapperPlayClientPlayerFlying entirely while remaining connected.
 *
 * Normal behavior:
 * - Pre-1.17: Client sends a flying packet every tick (~50ms)
 * - 1.17+: Client sends position sync at least every 20 ticks (~1 second)
 * - 1.21.2+: CLIENT_TICK_END replaces idle flying packets for stationary players
 *
 * ⚠ In 1.21.2+ this check has limited utility since stationary players legitimately
 * send only CLIENT_TICK_END and no flying packets. The flag threshold should be high.
 */
@CheckData(name = "FreecamB", stableKey = "void.freecam.no_flying",
        description = "No movement packets while player is responsive",
        decay = 0.005, setback = -1)
public class FreecamB extends Check implements PacketCheck {

    private long lastFlyingPacketTime;
    private long lastFlagTime = 0;
    private long joinTime;

    // How long without flying packets before flagging (milliseconds)
    private static final long NO_FLYING_THRESHOLD_MS = 10_000;  // 10 seconds
    private static final long FLAG_INTERVAL_MS = 10_000;        // Flag every 10 seconds

    public FreecamB(VoidPlayer player) {
        super(player);
        this.joinTime = System.currentTimeMillis();
        this.lastFlyingPacketTime = System.currentTimeMillis();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long now = System.currentTimeMillis();

        // Don't check in the first 10 seconds after join
        if (now - joinTime < 10_000) {
            lastFlyingPacketTime = now;
            return;
        }

        // If we receive any flying packet, reset the timer
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            lastFlyingPacketTime = now;
            if (lastFlagTime != 0) {
                reward();
                lastFlagTime = 0;
            }
            return;
        }

        // Exempt spectator mode, vehicles, bed, dead, creative flight
        if (player.gamemode == GameMode.SPECTATOR
                || player.inVehicle()
                || player.isInBed
                || player.compensatedEntities.self.isDead
                || player.isFlying) {
            lastFlyingPacketTime = now;
            return;
        }

        // Check on transaction responses and TICK_END (proof that player is alive and ticking)
        boolean isProofOfLife = isTransaction(event.getPacketType())
                || event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END
                || event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE;

        if (!isProofOfLife) return;

        long timeSinceFlying = now - lastFlyingPacketTime;

        if (timeSinceFlying >= NO_FLYING_THRESHOLD_MS) {
            if (now - lastFlagTime >= FLAG_INTERVAL_MS) {
                lastFlagTime = now;
                flagAndAlert("noFlying=" + (timeSinceFlying / 1000) + "s");
            }
        }
    }
}
