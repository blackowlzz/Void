package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.NotNull;

public record BridgeFrame(@NotNull MessageType type,
                          @NotNull String origin,
                          long timestamp,
                          byte @NotNull [] payload) {
}
