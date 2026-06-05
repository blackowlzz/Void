package ac.voidac.checks.impl.inventory;

import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.InventoryCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

@CheckData(name = "InventoryB", stableKey = "void.inventory.b", setback = 3, description = "Started digging blocks while inventory is open")
public class InventoryB extends InventoryCheck {
    public InventoryB(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        super.onPacketReceive(event);

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);

            if (wrapper.getAction() != DiggingAction.START_DIGGING) return;

            if (player.inventory.hasInventoryOpen()) {
                if (flagAndAlert()) {
                    if (shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                    if (!isNoSetbackPermission()) closeInventory();
                }
            } else {
                reward();
            }
        }
    }
}
