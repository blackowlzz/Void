package ac.voidac.checks.type;

import ac.voidac.api.AbstractCheck;
import ac.voidac.utils.anticheat.update.PositionUpdate;

public interface PositionCheck extends AbstractCheck {

    default void onPositionUpdate(final PositionUpdate positionUpdate) {
    }
}
