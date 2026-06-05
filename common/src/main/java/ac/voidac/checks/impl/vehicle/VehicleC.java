package ac.voidac.checks.impl.vehicle;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.player.VoidPlayer;

@CheckData(name = "VehicleC", stableKey = "void.vehicle.vehicle_control")
public class VehicleC extends Check {
    public VehicleC(VoidPlayer player) {
        super(player);
    }
}
