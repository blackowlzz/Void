package ac.voidac.utils.anticheat;

import java.util.UUID;

public final class BedrockPlayers {
    private BedrockPlayers() {
    }

    public static boolean isFloodgate(UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L;
    }
}
