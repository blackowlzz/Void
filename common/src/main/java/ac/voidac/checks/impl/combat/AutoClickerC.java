package ac.voidac.checks.impl.combat;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PostPredictionCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;

@CheckData(
        name = "AutoClickerC",
        stableKey = "void.combat.autoclickerc",
        description = "Sent multiple attack packets within a single client tick",
        decay = 0.05,
        setback = 15
)
public class AutoClickerC extends Check implements PostPredictionCheck {
    private int attacksThisTick = 0;
    private int consecutiveMultiTicks = 0;

    private int maxAttacksPerTick;
    private int minConsecutiveTicks;

    public AutoClickerC(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxAttacksPerTick = config.getIntElse(getConfigName() + ".max-attacks-per-tick", 1);
        minConsecutiveTicks = config.getIntElse(getConfigName() + ".min-consecutive-ticks", 3);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (AttackPackets.isExempt(player)) return;

        if (isTickPacket(event.getPacketType())) {
            endTick();
            return;
        }

        if (AttackPackets.isAttack(event)) {
            attacksThisTick++;
        }
    }

    private void endTick() {
        if (attacksThisTick > maxAttacksPerTick) {
            if (++consecutiveMultiTicks >= minConsecutiveTicks) {
                flagAndAlert("attacks=" + attacksThisTick + " consecutive=" + consecutiveMultiTicks);
                consecutiveMultiTicks = 0;
            }
        } else {
            consecutiveMultiTicks = Math.max(0, consecutiveMultiTicks - 1);
        }
        attacksThisTick = 0;
    }
}
