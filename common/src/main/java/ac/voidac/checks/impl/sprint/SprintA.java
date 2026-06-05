package ac.voidac.checks.impl.sprint;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "SprintA", stableKey = "void.sprint.hunger", description = "Sprinting with too low hunger", setback = 0)
public class SprintA extends Check implements PacketCheck {

    public SprintA(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            // Players can sprint if they're able to fly (MCP)
            // Players can also sprint if they are on a camel, regardless of their hunger level
            if (player.canFly || EntityTypes.isTypeInstanceOf(player.getVehicleType(), EntityTypes.CAMEL)) return;

            if (player.food < 6.0F && player.isSprinting) {
                if (flagAndAlert("hunger=" + player.food)) {
                    // Cancel the packet
                    if (shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                    if (shouldSetback()) {
                        player.getSetbackTeleportUtil().executeNonSimulatingSetback();
                    }
                }
            } else {
                reward();
            }
        }
    }
}
