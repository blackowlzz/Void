package ac.voidac.checks.impl.combat;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PostPredictionCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * Detects autoclickers by catching multiple ATTACK packets within a single
 * client tick (the interval between consecutive FLYING packets, ~50 ms on 1.8).
 *
 * A vanilla Minecraft client can only send one left-click attack per client
 * tick because mouse input is sampled once per game loop iteration.
 * Sending more than one attack in the same tick requires either:
 *   - An external autoclicker tool injecting packets directly
 *   - A modified client with a faster click loop
 *
 * This check has essentially zero false positives: no legitimate vanilla or
 * OptiFine client can trigger it regardless of technique (butterfly, jitter,
 * drag clicking).  The only exception would be edge-case packet-reordering
 * artefacts from ViaVersion, which is why we require min-consecutive-ticks
 * (default: 3) consecutive violations before flagging.
 *
 * @see AutoClickerA for per-click interval regularity detection
 * @see AutoClickerB for per-second CPS bucket variance detection
 */
@CheckData(
        name = "AutoClickerC",
        stableKey = "void.combat.autoclickerc",
        description = "Sent multiple attack packets within a single client tick",
        decay = 0.05,
        setback = 15
)
public class AutoClickerC extends Check implements PostPredictionCheck {

    // Attacks registered in the current client tick
    private int attacksThisTick = 0;
    // Consecutive ticks that had more attacks than allowed
    private int consecutiveMultiTicks = 0;

    private int maxAttacksPerTick;
    private int minConsecutiveTicks;

    public AutoClickerC(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        // Vanilla clients send at most 1 attack per tick; keep at 1
        maxAttacksPerTick = config.getIntElse(getConfigName() + ".max-attacks-per-tick", 1);
        // Require N consecutive violations to absorb rare ViaVersion packet re-ordering
        minConsecutiveTicks = config.getIntElse(getConfigName() + ".min-consecutive-ticks", 3);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.disableVoid) return;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;

        // Evaluate at each tick boundary
        if (isTickPacket(event.getPacketType())) {
            if (attacksThisTick > maxAttacksPerTick) {
                if (++consecutiveMultiTicks >= minConsecutiveTicks) {
                    flagAndAlert("attacks=" + attacksThisTick + " consecutive=" + consecutiveMultiTicks);
                    // Reset to avoid repeated flag spam on the same session
                    consecutiveMultiTicks = 0;
                }
            } else {
                // Clean tick decay the consecutive counter gradually so a single
                // clean tick does not immediately reset a long violation streak
                consecutiveMultiTicks = Math.max(0, consecutiveMultiTicks - 1);
            }
            attacksThisTick = 0;
            return;
        }

        boolean isAttack = event.getPacketType() == PacketType.Play.Client.ATTACK;
        if (!isAttack && event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            isAttack = packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        }
        if (!isAttack) return;

        attacksThisTick++;
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        // no-op violation decay is handled by the Check base class reward/decay system
    }
}
