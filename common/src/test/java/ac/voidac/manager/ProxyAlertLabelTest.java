package ac.voidac.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyAlertLabelTest {

    @Test
    void linkedServerShowsItsBridgeName() {
        assertEquals("SMP", ProxyAlertLabel.of(true, "SMP"));
    }

    @Test
    void surroundingWhitespaceIsDropped() {
        assertEquals("SMP", ProxyAlertLabel.of(true, "  SMP "));
    }

    @Test
    void withoutTheBridgeTheOldProxyLabelStays() {
        assertEquals("proxy", ProxyAlertLabel.of(false, "SMP"));
    }

    @Test
    void blankNameFallsBackToTheProxyLabel() {
        assertEquals("proxy", ProxyAlertLabel.of(true, "   "));
    }

    @Test
    void miniMessageTagsInTheNameAreEscaped() {
        assertEquals("\\<red>SMP", ProxyAlertLabel.of(true, "<red>SMP"));
    }
}
