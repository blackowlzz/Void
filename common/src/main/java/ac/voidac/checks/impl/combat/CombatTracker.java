package ac.voidac.checks.impl.combat;

import ac.voidac.checks.Check;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;

public class CombatTracker extends Check implements PacketCheck {
    private long tick;
    private long lastAttackTick = Long.MIN_VALUE / 2;

    public CombatTracker(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isKillauraDedicatedEnabled()) return;
        if (AttackPackets.isAttack(event)) lastAttackTick = tick;
        if (isTickPacket(event.getPacketType())) tick++;
    }

    public long currentTick() {
        return tick;
    }

    public long ticksSinceAttack() {
        return tick - lastAttackTick;
    }
}
