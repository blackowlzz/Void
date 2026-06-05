package ac.voidac.utils.inventory.slot;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.inventory.InventoryStorage;
import com.github.retrooper.packetevents.protocol.item.ItemStack;

public class ResultSlot extends Slot {

    public ResultSlot(InventoryStorage container, int slot) {
        super(container, slot);
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public void onTake(VoidPlayer player, ItemStack itemStack) {
        // Resync the player's inventory
    }
}
