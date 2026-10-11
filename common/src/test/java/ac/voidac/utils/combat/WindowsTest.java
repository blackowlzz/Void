package ac.voidac.utils.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsTest {
    @Test
    void booleanWindowSlides() {
        BooleanWindow window = new BooleanWindow(4);
        for (int i = 0; i < 4; i++) window.add(true);
        assertTrue(window.isFull());
        assertEquals(4, window.trueCount());
        window.add(false);
        window.add(false);
        assertEquals(2, window.trueCount());
        window.clear();
        assertFalse(window.isFull());
        assertEquals(0, window.trueCount());
    }
}
