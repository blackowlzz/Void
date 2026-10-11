package ac.voidac.utils.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepEstimatorTest {
    private static double clientStep(double sensitivity) {
        double f = sensitivity * 0.6 + 0.2;
        return f * f * f * 8 * 0.15;
    }

    private static float[] humanYaws(double step, int moves, long seed) {
        Random random = new Random(seed);
        float[] yaws = new float[moves + 1];
        yaws[0] = 217.31f;
        for (int i = 1; i <= moves; i++) {
            int dots = (int) Math.round(random.nextGaussian() * 6);
            yaws[i] = (float) (yaws[i - 1] + (float) (dots * step));
        }
        return yaws;
    }

    @Test
    void learnsTheTrueStepFromMouseMovement() {
        for (double sensitivity : new double[]{0.2, 0.35, 0.5, 0.7, 1.0}) {
            double step = clientStep(sensitivity);
            StepEstimator estimator = new StepEstimator();
            float[] yaws = humanYaws(step, 300, 11);
            for (int i = 1; i < yaws.length; i++) estimator.observe(yaws[i] - yaws[i - 1], step * 0.99);
            assertEquals(step, estimator.step(), step * 0.002, "sensitivity=" + sensitivity);
            assertTrue(estimator.isFresh(), "sensitivity=" + sensitivity);
        }
    }

    @Test
    void learnedStepKeepsLargeFlicksOnGrid() {
        double step = clientStep(0.5);
        StepEstimator estimator = new StepEstimator();
        float[] yaws = humanYaws(step, 200, 5);
        for (int i = 1; i < yaws.length; i++) estimator.observe(yaws[i] - yaws[i - 1], step * 1.008);

        float yaw = yaws[yaws.length - 1];
        for (int dots : new int[]{40, 150, 380, 700}) {
            float next = (float) (yaw + (float) (dots * step));
            assertFalse(SensitivityGrid.isOffGrid(next - yaw, estimator.step(), Math.abs(next)), "dots=" + dots);
        }
    }

    @Test
    void rawAnglesNeverFormAGrid() {
        StepEstimator estimator = new StepEstimator();
        Random random = new Random(2);
        for (int i = 0; i < 400; i++) estimator.observe(0.1 + random.nextDouble() * 5, 0.15);
        assertEquals(0, estimator.step(), 1e-12);
    }

    @Test
    void keepsTheLastGridWhenRawAnglesStart() {
        double step = clientStep(0.5);
        StepEstimator estimator = new StepEstimator();
        float[] yaws = humanYaws(step, 200, 9);
        for (int i = 1; i < yaws.length; i++) estimator.observe(yaws[i] - yaws[i - 1], step);
        double locked = estimator.step();
        assertTrue(locked > 0);

        Random random = new Random(4);
        for (int i = 0; i < 200; i++) estimator.observe(0.2 + random.nextDouble() * 5, locked);
        assertEquals(locked, estimator.step(), locked * 0.001);
        assertFalse(estimator.isFresh());
    }

    @Test
    void constantRawDeltasCannotCalibrateAFakeGrid() {
        double step = clientStep(0.5);
        StepEstimator estimator = new StepEstimator();
        float[] yaws = humanYaws(step, 200, 21);
        for (int i = 1; i < yaws.length; i++) estimator.observe(yaws[i] - yaws[i - 1], step);
        double locked = estimator.step();

        for (int i = 0; i < 400; i++) estimator.observe(2.8647919, locked);
        assertEquals(locked, estimator.step(), locked * 0.001);
    }

    @Test
    void relocksAfterASensitivityChange() {
        StepEstimator estimator = new StepEstimator();
        double first = clientStep(0.5);
        float[] yaws = humanYaws(first, 200, 31);
        for (int i = 1; i < yaws.length; i++) estimator.observe(yaws[i] - yaws[i - 1], first);

        double second = clientStep(0.8);
        float[] changed = humanYaws(second, 200, 32);
        for (int i = 1; i < changed.length; i++) estimator.observe(changed[i] - changed[i - 1], second);
        assertEquals(second, estimator.step(), second * 0.002);
    }

    @Test
    void constantRawDeltasDoNotDiluteTheGridFit() {
        double step = clientStep(0.5);
        StepEstimator estimator = new StepEstimator();
        float[] yaws = humanYaws(step, 40, 41);
        for (int i = 1; i < yaws.length; i++) estimator.observe(yaws[i] - yaws[i - 1], step * 0.992);
        double locked = estimator.step();
        assertTrue(locked > 0);

        for (int i = 0; i < 56; i++) estimator.observe(2.8647919, 0.1487);
        assertEquals(step, estimator.step(), step * 0.001);
    }

    @Test
    void repeatedDeltasNeverBecomeTheStep() {
        StepEstimator estimator = new StepEstimator();
        for (int i = 0; i < 300; i++) estimator.observe(0.4, 0.4);
        assertEquals(0, estimator.step(), 1e-12);

        StepEstimator large = new StepEstimator();
        for (int i = 0; i < 300; i++) large.observe(2.8647919, 2.8647888);
        assertEquals(0, large.step(), 1e-12);
    }

    @Test
    void smoothedRotationNeverGoesFresh() {
        StepEstimator estimator = new StepEstimator();
        double yaw = 0;
        double target = 40;
        for (int i = 0; i < 300; i++) {
            double move = (target - yaw) * 0.2;
            yaw += move;
            estimator.observe(move, 0.15);
            if (i % 40 == 39) target += 55;
        }
        assertFalse(estimator.isFresh());
        assertEquals(0, estimator.step(), 1e-12);
    }

    @Test
    void fractionalMouseDeltasNeverGoFresh() {
        StepEstimator estimator = new StepEstimator();
        Random random = new Random(8);
        for (int i = 0; i < 300; i++) estimator.observe((0.2 + random.nextDouble() * 4) * 0.15 * 3.37, 0.15);
        assertFalse(estimator.isFresh());
        assertEquals(0, estimator.step(), 1e-12);
    }
}
