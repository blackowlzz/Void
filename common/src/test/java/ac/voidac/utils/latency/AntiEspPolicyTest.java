package ac.voidac.utils.latency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AntiEspPolicyTest {

    @Test
    void calculatesMaskedRowsAcrossNegativeWorldHeight() {
        assertEquals(16, AntiEspPolicy.maskedRows(-64, 0, 0));
        assertEquals(16, AntiEspPolicy.maskedRows(-64, 3, 0));
        assertEquals(0, AntiEspPolicy.maskedRows(-64, 4, 0));
        assertEquals(8, AntiEspPolicy.maskedRows(-64, 4, 8));
        assertEquals(0, AntiEspPolicy.maskedRows(0, 0, 0));
    }
}
