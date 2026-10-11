package ac.voidac.utils.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateOffsetToleranceTest {
    private static final double TELEPORT_X = 94.00453494201301;
    private static final double ROUND_TRIP_X = (TELEPORT_X - 53210) + 53210;

    @Test
    void roundTripErrorIsRejectedWhenCompatIsOff() {
        assertFalse(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, ROUND_TRIP_X, 0, false));
    }

    @Test
    void roundTripErrorIsAcceptedWhenCompatIsOn() {
        assertTrue(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, ROUND_TRIP_X, 0, true));
    }

    @Test
    void exactMatchIsAcceptedEitherWay() {
        assertTrue(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, TELEPORT_X, 0, false));
        assertTrue(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, TELEPORT_X, 0, true));
    }

    @Test
    void differenceAboveToleranceIsRejectedWhenCompatIsOn() {
        assertFalse(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, TELEPORT_X + 1e-5, 0, true));
    }

    @Test
    void largerRelativeThresholdIsKept() {
        assertTrue(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, TELEPORT_X + 0.02, 0.03, true));
        assertTrue(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, TELEPORT_X + 0.02, 0.03, false));
    }

    @Test
    void nanNeverMatches() {
        assertFalse(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, Double.NaN, 0, true));
        assertFalse(CoordinateOffsetTolerance.horizontalMatch(TELEPORT_X, Double.NaN, 0, false));
    }
}
