package ac.voidac.utils.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitivityGridTest {
    private static final double STEP = 0.15;

    @Test
    void multiplesOfTheStepAreOnGrid() {
        float yaw = 130.2f;
        for (int dots = 1; dots < 40; dots++) {
            float next = yaw + (float) (dots * STEP);
            assertFalse(SensitivityGrid.isOffGrid(next - yaw, STEP, next), "dots=" + dots);
        }
    }

    @Test
    void largeYawKeepsFloatNoiseOnGrid() {
        float yaw = 28000.37f;
        float next = yaw + (float) (7 * STEP);
        assertFalse(SensitivityGrid.isOffGrid(next - yaw, STEP, next));
    }

    @Test
    void rawTrigonometricAnglesFallOffGrid() {
        int offGrid = 0;
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 1000; i++) {
            double angle = 1 + random.nextDouble() * 60;
            if (SensitivityGrid.isOffGrid(angle, STEP, 90)) offGrid++;
        }
        assertTrue(offGrid > 940, "offGrid=" + offGrid);
    }
}
