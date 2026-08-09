package ac.voidac.bridge.protocol.payload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Lifts a ban across the network.
 * Carries both a UUID and a name, because unbanning by name is how staff
 * actually do it and the origin may never have had the UUID.
 */
public record UnbanPayload(@Nullable UUID uuid,
                           @NotNull String playerName,
                           @NotNull String banId) {

    public byte[] encode() {
        return PayloadIo.write(out -> {
            PayloadIo.writeUuid(out, uuid);
            out.writeUTF(playerName);
            out.writeUTF(banId);
        });
    }

    public static @Nullable UnbanPayload decode(byte @NotNull [] payload) {
        return PayloadIo.read(payload, in -> new UnbanPayload(
                PayloadIo.readUuid(in),
                in.readUTF(),
                in.readUTF()
        ));
    }
}
