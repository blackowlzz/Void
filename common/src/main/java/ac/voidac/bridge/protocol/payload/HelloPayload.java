package ac.voidac.bridge.protocol.payload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record HelloPayload(@NotNull String voidVersion) {
    public byte[] encode() {
        return PayloadIo.write(out -> out.writeUTF(voidVersion));
    }

    public static @Nullable HelloPayload decode(byte @NotNull [] payload) {
        return PayloadIo.read(payload, in -> new HelloPayload(in.readUTF()));
    }
}
