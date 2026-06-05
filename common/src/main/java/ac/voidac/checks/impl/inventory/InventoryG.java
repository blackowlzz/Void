package ac.voidac.checks.impl.inventory;

import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.InventoryCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

@CheckData(name = "InventoryG", stableKey = "void.inventory.g", setback = 3, experimental = true, description = "Sent an entity action packet while inventory is open")
public class InventoryG extends InventoryCheck {
    public InventoryG(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.packetStateData.lastPacketWasTeleport) return;
        super.onPacketReceive(event);

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);
            WrapperPlayClientEntityAction.Action action = wrapper.getAction();

            if (action == WrapperPlayClientEntityAction.Action.STOP_SNEAKING
                    || action == WrapperPlayClientEntityAction.Action.STOP_SPRINTING) {
                return;
            }

            if (player.inventory.hasInventoryOpen()) {
                if (flagAndAlert() && !isNoSetbackPermission()) {
                    closeInventory();
                }
            } else {
                reward();
            }
        }
    }
}
