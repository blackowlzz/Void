package ac.voidac.bridge.protocol;

import java.util.UUID;

/**
 * Wire constants shared by the backend and the proxy plugin.
 * Keep this dependency-free, the proxy jar shades this package and nothing else.
 */
public final class BridgeProtocol {

    private BridgeProtocol() {
    }

    public static final String CHANNEL = "voidac:bridge";

    /** Bump only on incompatible frame changes. Mismatches get dropped, not guessed at. */
    public static final byte VERSION = 1;

    // so random junk on the channel dies before we waste a Mac on it
    public static final byte MAGIC_0 = 'V';
    public static final byte MAGIC_1 = 'B';

    public static final int MAC_LENGTH = 32;

    // yes it's wide. "my proxy and my box disagree by 40 seconds" is a real
    // thing on rented hardware, and a silently dead bridge is hell to debug
    public static final long MAX_CLOCK_SKEW_MS = 60_000L;

    /** Must outlive the skew window both ways, or a still-fresh frame could find its nonce already gone. */
    public static final long NONCE_TTL_MS = MAX_CLOCK_SKEW_MS * 3;

    /** Stops a corrupt length prefix from turning into an OOM. A big SYNC is nowhere near this. */
    public static final int MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;

    /** Used when we genuinely don't know the player's UUID. */
    public static final UUID UNKNOWN_UUID = new UUID(0L, 0L);
}
