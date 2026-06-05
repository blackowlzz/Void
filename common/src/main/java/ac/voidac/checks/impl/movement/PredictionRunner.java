package ac.voidac.checks.impl.movement;

import ac.voidac.checks.Check;
import ac.voidac.checks.type.PositionCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.PositionUpdate;

public class PredictionRunner extends Check implements PositionCheck {
    public PredictionRunner(VoidPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPositionUpdate(final PositionUpdate positionUpdate) {
        if (!player.inVehicle()) {
            player.movementCheckRunner.processAndCheckMovementPacket(positionUpdate);
        }
    }
}
