package ac.voidac.checks.type;

import ac.voidac.api.AbstractCheck;
import ac.voidac.utils.anticheat.update.RotationUpdate;

public interface RotationCheck extends AbstractCheck {

    default void process(final RotationUpdate rotationUpdate) {
    }
}
