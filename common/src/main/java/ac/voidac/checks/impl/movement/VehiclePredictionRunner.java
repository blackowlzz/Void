package ac.voidac.checks.impl.movement;

import ac.voidac.checks.Check;
import ac.voidac.checks.type.VehicleCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.PositionUpdate;
import ac.voidac.utils.anticheat.update.VehiclePositionUpdate;

public class VehiclePredictionRunner extends Check implements VehicleCheck {
    public VehiclePredictionRunner(VoidPlayer playerData) {
        super(playerData);
    }

    @Override
    public void process(final VehiclePositionUpdate vehicleUpdate) {
        // Vehicle onGround = false always
        // We don't do vehicle setbacks because vehicle netcode sucks.
        player.movementCheckRunner.processAndCheckMovementPacket(new PositionUpdate(vehicleUpdate.from(), vehicleUpdate.to(), false, null, null, vehicleUpdate.isTeleport()));
    }
}
