package ac.voidac.checks.impl.combat;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PostPredictionCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.BedrockPlayers;
import ac.voidac.utils.combat.BooleanWindow;
import ac.voidac.utils.combat.FullCharge;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;

@CheckData(
        name = "AutoClickerD",
        stableKey = "void.combat.autoclickerd",
        description = "Attacked at the earliest full-charge tick almost every time",
        killauraDedicated = true,
        decay = 0.05
)
public class AutoClickerD extends Check implements PostPredictionCheck {
    private static final int WINDOW = 48;
    private static final int MAX_INTERVAL = 40;

    private final BooleanWindow atFullCharge = new BooleanWindow(WINDOW);
    private long tick;
    private long lastAttackTick = -1;

    private int minInBand;
    private int minFloor;

    public AutoClickerD(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minInBand = config.getIntElse(getConfigName() + ".min-in-band", 48);
        minFloor = config.getIntElse(getConfigName() + ".min-floor", 6);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacket(event.getPacketType())) tick++;
        if (!isFeatureEnabled() || AttackPackets.isExempt(player) || BedrockPlayers.isFloodgate(player.uuid) || !AttackPackets.isAttack(event)) return;
        if (tick == lastAttackTick) return;

        long interval = tick - lastAttackTick;
        boolean first = lastAttackTick < 0;
        lastAttackTick = tick;
        if (first) return;

        int floor = FullCharge.ticks(player.compensatedEntities.self.getAttributeValue(Attributes.ATTACK_SPEED));
        if (interval > MAX_INTERVAL || floor < minFloor) {
            atFullCharge.clear();
            return;
        }

        atFullCharge.add(interval == floor || interval == floor + 1);
        if (atFullCharge.isFull() && atFullCharge.trueCount() >= minInBand) {
            flagAndAlert("attacks=" + atFullCharge.trueCount() + "/" + WINDOW + " at " + floor + "-" + (floor + 1) + " ticks");
            atFullCharge.clear();
        } else {
            reward();
        }
    }
}
