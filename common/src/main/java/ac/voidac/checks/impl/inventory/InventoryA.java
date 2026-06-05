package ac.voidac.checks.impl.inventory;

import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.InventoryCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity.InteractAction;

@CheckData(name = "InventoryA", stableKey = "void.inventory.a", setback = 3, description = "Attacked an entity while inventory is open")
public class InventoryA extends InventoryCheck {
    public InventoryA(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        super.onPacketReceive(event);

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);

            if (wrapper.getAction() != InteractAction.ATTACK) return;

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
