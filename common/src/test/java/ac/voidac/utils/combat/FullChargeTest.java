package ac.voidac.utils.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullChargeTest {
    @Test
    void matchesTheClientCooldownProgress() {
        for (double speed : new double[]{0.6, 1.0, 1.2, 1.6, 2.0, 4.0}) {
            double period = 20.0 / speed;
            int ticks = FullCharge.ticks(speed);
            assertEquals(true, (ticks + 0.5) / period >= 1, "speed=" + speed);
            assertEquals(true, (ticks - 1 + 0.5) / period < 1, "speed=" + speed);
        }
    }

    @Test
    void floatPrecisionDoesNotShiftTheTick() {
        assertEquals(12, FullCharge.ticks(1.5999999046325684));
        assertEquals(20, FullCharge.ticks(1.0000000149011612));
        assertEquals(12, FullCharge.ticks(1.6000000238418579));
    }

    @Test
    void swordAxeAndFistValues() {
        assertEquals(12, FullCharge.ticks(1.6));
        assertEquals(20, FullCharge.ticks(1.0));
        assertEquals(5, FullCharge.ticks(4.0));
    }

    @Test
    void invalidSpeedGivesZero() {
        assertEquals(0, FullCharge.ticks(0));
        assertEquals(0, FullCharge.ticks(-1));
    }
}
