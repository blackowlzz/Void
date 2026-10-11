package ac.voidac.utils.anticheat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadDeveloperTest {
    private static final UUID DEVELOPER = UUID.fromString("d1443f2a-a9dc-4082-b775-a932a723a586");

    @Test
    void matchesTheDeveloperUuidUnderAnyName() {
        assertTrue(LeadDeveloper.matches(DEVELOPER, "SomeoneElse"));
        assertTrue(LeadDeveloper.matches(DEVELOPER, null));
    }

    @Test
    void matchesTheDeveloperNameIgnoringCase() {
        assertTrue(LeadDeveloper.matches(UUID.randomUUID(), "22ix"));
        assertTrue(LeadDeveloper.matches(UUID.randomUUID(), "22IX"));
        assertTrue(LeadDeveloper.matches(null, "22ix"));
    }

    @Test
    void ignoresOtherPlayers() {
        assertFalse(LeadDeveloper.matches(UUID.randomUUID(), "Steve"));
        assertFalse(LeadDeveloper.matches(UUID.randomUUID(), "22ix_"));
        assertFalse(LeadDeveloper.matches(null, null));
    }
}
