package ac.voidac.checks.impl.packetorder;

import ac.voidac.checks.ClientCheckApplicability;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketOrderVersionCompatibilityTest {
    @Test
    void packetOrderAOnlyAppliesThrough1122() {
        assertTrue(ClientCheckApplicability.packetOrderA(ClientVersion.V_1_12_2));
        assertFalse(ClientCheckApplicability.packetOrderA(ClientVersion.V_1_13));
    }

    @Test
    void packetOrderHOnlyAppliesBefore1216() {
        assertTrue(ClientCheckApplicability.packetOrderH(ClientVersion.V_1_21_5));
        assertFalse(ClientCheckApplicability.packetOrderH(ClientVersion.V_1_21_6));
    }

    @Test
    void packetOrderKOnlyAppliesBefore112() {
        assertTrue(ClientCheckApplicability.packetOrderK(ClientVersion.V_1_11_1));
        assertFalse(ClientCheckApplicability.packetOrderK(ClientVersion.V_1_12));
    }
}
