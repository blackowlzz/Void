package ac.voidac.predictionengine.blockeffects;

import ac.voidac.player.VoidPlayer;

import java.util.List;

public interface BlockEffectsResolver {

    void applyEffectsFromBlocks(VoidPlayer player, List<VoidPlayer.Movement> movements);

}
