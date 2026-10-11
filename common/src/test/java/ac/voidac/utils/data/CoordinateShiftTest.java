package ac.voidac.utils.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateShiftTest {
    @Test
    void staysZeroWithoutCapture() {
        CoordinateShift shift = new CoordinateShift();
        shift.learn(120.5, -40.25);

        assertEquals(120.5, shift.toRealX(120.5));
        assertEquals(-40, shift.toRealBlockZ(-40));
    }

    @Test
    void learnsAPositiveOffset() {
        CoordinateShift shift = new CoordinateShift();
        double realX = 94.00453494201301;
        double realZ = -299.88104616966876;
        shift.capture(realX - 53210, realZ - 1024);
        shift.learn(realX, realZ);

        assertEquals(realX, shift.toRealX(realX - 53210), 1e-9);
        assertEquals(realZ, shift.toRealZ(realZ - 1024), 1e-9);
        assertEquals(94, shift.toRealBlockX(94 - 53210));
        assertEquals(-300, shift.toRealBlockZ(-300 - 1024));
    }

    @Test
    void learnsANegativeOffset() {
        CoordinateShift shift = new CoordinateShift();
        shift.capture(10.75 + 87344, 3.5 + 16);
        shift.learn(10.75, 3.5);

        assertEquals(10.75, shift.toRealX(10.75 + 87344), 1e-9);
        assertEquals(3, shift.toRealBlockZ(3 + 16));
    }

    @Test
    void ignoresNonIntegerDifference() {
        CoordinateShift shift = new CoordinateShift();
        shift.capture(100.0 - 512, 100.0 - 512);
        shift.learn(100.0, 100.0);
        shift.capture(150.3, 150.3);
        shift.learn(150.0, 150.0);

        assertEquals(100.0, shift.toRealX(100.0 - 512), 1e-9);
    }

    @Test
    void captureIsConsumedByLearn() {
        CoordinateShift shift = new CoordinateShift();
        shift.capture(100.0 - 512, 100.0 - 512);
        shift.learn(100.0, 100.0);
        shift.learn(7.0, 7.0);

        assertEquals(-412, shift.toRealBlockX(-924));
    }
}
