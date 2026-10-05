package ac.voidac.checks.impl.badpackets;

import ac.voidac.checks.ClientCheckApplicability;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadPacketsVersionCompatibilityTest {
    @Test
    void duplicateSneakOnlyAppliesBefore1212() {
        assertTrue(ClientCheckApplicability.duplicateSneak(ClientVersion.V_1_21));
        assertFalse(ClientCheckApplicability.duplicateSneak(ClientVersion.V_1_21_2));
    }

    @Test
    void playerInputAndBundleChecksOnlyApplyFrom1212() {
        assertFalse(ClientCheckApplicability.playerInputAndBundle(ClientVersion.V_1_21));
        assertTrue(ClientCheckApplicability.playerInputAndBundle(ClientVersion.V_1_21_2));
    }

    @Test
    void boatPaddleChecksOnlyApplyFrom19() {
        assertFalse(ClientCheckApplicability.boatPaddles(ClientVersion.V_1_8));
        assertTrue(ClientCheckApplicability.boatPaddles(ClientVersion.V_1_9));
    }
}
