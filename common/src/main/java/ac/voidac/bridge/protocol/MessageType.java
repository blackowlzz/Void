package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.Nullable;

/**
 * Message kinds on the bridge channel.
 * Ids are wire format: never renumber, only append.
 */
public enum MessageType {

    /** Backend to proxy: "I'm up, what did I miss?" */
    HELLO((byte) 0x01),

    /** Proxy to backend: the full ban list, answer to HELLO. */
    SYNC((byte) 0x02),

    /** Backend flagged someone, proxy fans it out. */
    ALERT((byte) 0x10),

    /** Backend banned someone, proxy records it and pushes it everywhere. */
    BAN((byte) 0x20),

    UNBAN((byte) 0x21);

    private final byte id;

    MessageType(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }

    /** Null when this build doesn't know the id, probably a peer running something newer. */
    public static @Nullable MessageType byId(byte id) {
        for (MessageType type : VALUES) {
            if (type.id == id) return type;
        }
        return null;
    }

    // values() allocates every call and decode runs per frame
    private static final MessageType[] VALUES = values();
}
