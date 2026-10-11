package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.Nullable;

public enum MessageType {
    HELLO((byte) 0x01),

    SYNC((byte) 0x02),

    ALERT((byte) 0x10),

    BAN((byte) 0x20),

    UNBAN((byte) 0x21);

    private final byte id;

    MessageType(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }

    public static @Nullable MessageType byId(byte id) {
        for (MessageType type : VALUES) {
            if (type.id == id) return type;
        }
        return null;
    }

    private static final MessageType[] VALUES = values();
}
