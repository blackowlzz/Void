package ac.voidac.bridge.protocol.payload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
