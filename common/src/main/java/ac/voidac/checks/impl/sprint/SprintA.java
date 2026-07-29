package ac.voidac.checks.impl.sprint;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PostPredictionCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;

@CheckData(name = "SprintA", stableKey = "void.sprint.hunger", description = "Sprinting with too low hunger", setback = 0)
public class SprintA extends Check implements PostPredictionCheck {

    public SprintA(VoidPlayer player) {
        super(player);
    }

    // Runs post-prediction rather than on the flying packet: food and sprinting are settled by
    // then, so we aren't judging a tick whose state is still half-applied.
    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        // Players can sprint if they're able to fly
        // Players can also sprint if they are on a camel, regardless of their hunger level
        if (player.canFly || EntityTypes.isTypeInstanceOf(player.getVehicleType(), EntityTypes.CAMEL)) return;

        // Vanilla treats food <= 6 as hungry, and the hungry cannot sprint. The old `< 6` let a
        // player sprint at exactly 6 food unchallenged.
        if (player.food <= 6.0F) {
            if (player.isSprinting) {
                if (flagAndAlert("hunger=" + player.food)) {
                    if (shouldModifyPackets()) {
                        player.onPacketCancel();
                    }
                    setbackIfAboveSetbackVL();
                }
            } else {
                reward();
            }
        }
    }
}
