package ac.voidac.utils.anticheat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPlayersTest {
    @Test
    void floodgateUuidsHaveAZeroMostSignificantHalf() {
        assertTrue(BedrockPlayers.isFloodgate(new UUID(0L, 2535412345678901L)));
    }

    @Test
    void javaUuidsAreNotBedrock() {
        assertFalse(BedrockPlayers.isFloodgate(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")));
        assertFalse(BedrockPlayers.isFloodgate(UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes())));
    }

    @Test
    void missingUuidIsNotBedrock() {
        assertFalse(BedrockPlayers.isFloodgate(null));
    }
}
