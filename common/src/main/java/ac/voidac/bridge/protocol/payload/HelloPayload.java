package ac.voidac.bridge.protocol.payload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * "I'm up, what did I miss?"
 * Proxy answers with every active ban, which is how a server that was offline
 * during a ban wave catches up.
 */
public record HelloPayload(@NotNull String voidVersion) {

    public byte[] encode() {
        return PayloadIo.write(out -> out.writeUTF(voidVersion));
    }

    public static @Nullable HelloPayload decode(byte @NotNull [] payload) {
        return PayloadIo.read(payload, in -> new HelloPayload(in.readUTF()));
    }
}
