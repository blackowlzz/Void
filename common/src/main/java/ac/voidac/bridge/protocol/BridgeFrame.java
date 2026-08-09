package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.NotNull;

/** A frame that survived signature, freshness and replay checks. Payload still needs decoding. */
public record BridgeFrame(@NotNull MessageType type,
                          @NotNull String origin,
                          long timestamp,
                          byte @NotNull [] payload) {
}
