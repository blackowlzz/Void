package ac.voidac.utils.anticheat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadDeveloperTest {
    @Test
    void matchesOnlyOnOnlineModeServers() {
        assertTrue(LeadDeveloper.matches(UUID.fromString("d1443f2a-a9dc-4082-b775-a932a723a586"), true));
        assertFalse(LeadDeveloper.matches(UUID.fromString("d1443f2a-a9dc-4082-b775-a932a723a586"), false));
    }

    @Test
    void ignoresOtherPlayers() {
        assertFalse(LeadDeveloper.matches(UUID.randomUUID(), true));
    }
}
