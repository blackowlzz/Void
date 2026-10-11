package ac.voidac.bridge.protocol.payload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record AlertPayload(@Nullable UUID playerUuid,
                           @NotNull String playerName,
                           @NotNull String checkName,
                           int violations,
                           @NotNull String message) {
    public byte[] encode() {
        return PayloadIo.write(out -> {
            PayloadIo.writeUuid(out, playerUuid);
            out.writeUTF(playerName);
            out.writeUTF(checkName);
            out.writeInt(violations);
            out.writeUTF(message);
        });
    }

    public static @Nullable AlertPayload decode(byte @NotNull [] payload) {
        return PayloadIo.read(payload, in -> new AlertPayload(
                PayloadIo.readUuid(in),
                in.readUTF(),
                in.readUTF(),
                in.readInt(),
                in.readUTF()
        ));
    }
}
