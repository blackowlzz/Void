package ac.voidac.predictionengine.blockeffects;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.math.Vector3dm;

import java.util.List;

public interface BlockEffectsResolver {

    void applyEffectsFromBlocks(VoidPlayer player, Vector3dm clientVelocity, boolean onlyApplyVelocity, List<VoidPlayer.Movement> movements);

}
