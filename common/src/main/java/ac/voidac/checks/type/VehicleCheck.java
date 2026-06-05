package ac.voidac.checks.type;

import ac.voidac.api.AbstractCheck;
import ac.voidac.utils.anticheat.update.VehiclePositionUpdate;

public interface VehicleCheck extends AbstractCheck {

    void process(final VehiclePositionUpdate vehicleUpdate);
}
