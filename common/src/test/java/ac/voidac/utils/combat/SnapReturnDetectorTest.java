package ac.voidac.utils.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapReturnDetectorTest {
    private SnapReturnDetector detector() {
        return new SnapReturnDetector(12, 2, 6);
    }

    @Test
    void snapAndReturnAroundAnAttackIsDetected() {
        SnapReturnDetector detector = detector();
        assertFalse(detector.accept(100, 40, -10, true));
        assertTrue(detector.accept(101, -40.3, 10.4, true));
    }

    @Test
    void returnFiveTicksLaterIsDetected() {
        SnapReturnDetector detector = detector();
        assertFalse(detector.accept(574, 48.55725, 3.184332, true));
        assertTrue(detector.accept(579, -48.55725, -3.184332, false));
    }

    @Test
    void returnAcrossTheYawSeamIsDetected() {
        SnapReturnDetector detector = detector();
        assertFalse(detector.accept(10, 340, 0, true));
        assertTrue(detector.accept(11, -339.5, 0.2, true));
    }

    @Test
    void flickWithoutReturnIsIgnored() {
        SnapReturnDetector detector = detector();
        assertFalse(detector.accept(100, 40, 0, true));
        assertFalse(detector.accept(101, 3, 0, true));
    }

    @Test
    void snapAwayFromCombatIsIgnored() {
        SnapReturnDetector detector = detector();
        assertFalse(detector.accept(100, 40, 0, false));
        assertFalse(detector.accept(101, -40, 0, false));
    }

    @Test
    void smallMovementsAreIgnored() {
        SnapReturnDetector detector = detector();
        assertFalse(detector.accept(100, 5, 0, true));
        assertFalse(detector.accept(101, -5, 0, true));
    }

    @Test
    void returnTooLateIsIgnored() {
        SnapReturnDetector detector = detector();
        assertFalse(detector.accept(100, 40, 0, true));
        assertFalse(detector.accept(108, -40, 0, true));
    }

    @Test
    void returnMoveIsNotReusedAsTheNextSnap() {
        SnapReturnDetector detector = detector();
        assertFalse(detector.accept(100, 40, 0, true));
        assertTrue(detector.accept(101, -40, 0, true));
        assertFalse(detector.accept(102, 40, 0, true));
        assertTrue(detector.accept(103, -40, 0, true));
    }
}
