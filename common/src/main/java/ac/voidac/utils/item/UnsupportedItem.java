package ac.voidac.utils.item;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.latency.CompensatedWorld;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;

public class UnsupportedItem extends ItemBehaviour {

    public static final UnsupportedItem INSTANCE = new UnsupportedItem();

    @Override
    public boolean canUse(ItemStack item, CompensatedWorld world, VoidPlayer player, InteractionHand hand) {
        return false;
    }

}
