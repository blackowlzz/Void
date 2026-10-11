package ac.voidac.bridge.protocol;

import java.util.UUID;

public final class BridgeProtocol {
    private BridgeProtocol() {
    }

    public static final String CHANNEL = "voidac:bridge";

    public static final byte VERSION = 1;

    public static final byte MAGIC_0 = 'V';
    public static final byte MAGIC_1 = 'B';

    public static final int MAC_LENGTH = 32;

    public static final long MAX_CLOCK_SKEW_MS = 60_000L;

    public static final long NONCE_TTL_MS = MAX_CLOCK_SKEW_MS * 3;

    public static final int MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;

    public static final UUID UNKNOWN_UUID = new UUID(0L, 0L);
}
