package ac.voidac.utils.item;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.latency.CompensatedWorld;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;

public class AlwaysUseItem extends ItemBehaviour {

    public static final AlwaysUseItem INSTANCE = new AlwaysUseItem();

    @Override
    public boolean canUse(ItemStack item, CompensatedWorld world, VoidPlayer player, InteractionHand hand) {
        return true;
    }

}
