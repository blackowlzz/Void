package ac.voidac.utils.blockplace;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.BlockPlace;

public interface BlockPlaceFactory {
    void applyBlockPlaceToWorld(VoidPlayer player, BlockPlace place);
}
