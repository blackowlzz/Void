package ac.voidac.checks.impl.inventory;

import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.InventoryCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

@CheckData(name = "InventoryE", stableKey = "void.inventory.e", setback = 3, description = "Sent a held item change packet while inventory is open")
public class InventoryE extends InventoryCheck {
    private long lastTransaction = Long.MAX_VALUE;

    public InventoryE(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        super.onPacketReceive(event);

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            if (player.inventory.hasInventoryOpen()) {
                // Skip the first hotbar packet after server forces a held item change
                if (this.lastTransaction < player.lastTransactionReceived.get()
                        && flagAndAlert()) {
                    if (shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                        player.inventory.needResend = true;
                    }
                    if (!isNoSetbackPermission()) closeInventory();
                }
            } else {
                reward();
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.HELD_ITEM_CHANGE) {
            this.lastTransaction = player.lastTransactionSent.get();
        }
    }
}
