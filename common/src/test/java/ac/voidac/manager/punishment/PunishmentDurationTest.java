package ac.voidac.manager.punishment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentDurationTest {

    @Test
    void acceptsSupportedDurations() {
        assertTrue(PunishmentDuration.isValid("7d"));
        assertTrue(PunishmentDuration.isValid("1d12h30m"));
        assertTrue(PunishmentDuration.isValid("permanent"));
        assertTrue(PunishmentDuration.isValid("PERM"));
        assertTrue(PunishmentDuration.isValid("0"));
    }

    @Test
    void rejectsReasonsAndPartialDurations() {
        assertFalse(PunishmentDuration.isValid("exploiting"));
        assertFalse(PunishmentDuration.isValid("7d exploiting"));
        assertFalse(PunishmentDuration.isValid("7dx"));
        assertFalse(PunishmentDuration.isValid("0d"));
        assertFalse(PunishmentDuration.isValid(""));
        assertFalse(PunishmentDuration.isValid("999999999999999999999999d"));
    }

    @Test
    void computesExpiryFromAStableClock() {
        long now = 1_800_000_000_000L;

        assertEquals(now + 129_600_000L, PunishmentDuration.parseExpiry("1d12h", now));
        assertEquals(0L, PunishmentDuration.parseExpiry("permanent", now));
        assertThrows(IllegalArgumentException.class, () -> PunishmentDuration.parseExpiry("exploiting", now));
    }

    @Test
    void normalizesCaseAndWhitespace() {
        assertEquals("7d", PunishmentDuration.normalize(" 7D "));
        assertEquals("permanent", PunishmentDuration.normalize(" PERMANENT "));
        assertThrows(IllegalArgumentException.class,
                () -> PunishmentDuration.normalize("999999999999999999999999d"));
    }
}
