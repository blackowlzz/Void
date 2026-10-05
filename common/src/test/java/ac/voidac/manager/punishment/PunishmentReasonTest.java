package ac.voidac.manager.punishment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentReasonTest {
    @Test
    void usesCustomReasonWhenProvided() {
        assertEquals("Combat hacks", PunishmentReason.resolve("  Combat hacks  ", "Cheating"));
    }

    @Test
    void usesConfiguredFallbackWhenReasonIsMissing() {
        assertEquals("Cheating", PunishmentReason.resolve(null, "Cheating"));
        assertEquals("Cheating", PunishmentReason.resolve("   ", "Cheating"));
    }

    @Test
    void usesSafeFallbackWhenConfigurationIsBlank() {
        assertEquals("Cheating", PunishmentReason.resolve(null, "   "));
    }

    @Test
    void replacesReasonPlaceholderInCurrentTemplates() {
        assertEquals("Header\nReason: Exploiting",
                PunishmentReason.applyToTemplate("Header\nReason: {reason}", "Exploiting"));
    }

    @Test
    void appendsReasonToLegacyTemplates() {
        assertEquals("Header\n&7Reason: &fExploiting",
                PunishmentReason.applyToTemplate("Header", "Exploiting"));
    }
}
