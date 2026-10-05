package ac.voidac.checks.impl.combat;

import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

final class AttackPackets {

    static final int IDLE_TICKS = 40;

    private AttackPackets() {
    }

    static boolean isExempt(VoidPlayer player) {
        return player.disableVoid || player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR;
    }

    static boolean isAttack(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Client.ATTACK) return true;
        return type == PacketType.Play.Client.INTERACT_ENTITY
                && new WrapperPlayClientInteractEntity(event).getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
    }
}
