package ac.voidac.checks.impl.inventory;

import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.InventoryCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.BlockPlace;

@CheckData(name = "InventoryC", stableKey = "void.inventory.c", setback = 3, description = "Placed a block while inventory is open")
public class InventoryC extends InventoryCheck {
    public InventoryC(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (player.inventory.hasInventoryOpen()) {
            if (flagAndAlert()) {
                if (shouldModifyPackets()) place.resync();
                if (!isNoSetbackPermission()) closeInventory();
            }
        } else {
            reward();
        }
    }
}
