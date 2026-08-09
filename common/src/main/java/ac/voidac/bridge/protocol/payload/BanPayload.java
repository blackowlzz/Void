package ac.voidac.bridge.protocol.payload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * One ban, as it should exist everywhere. banId keeps it idempotent when the
 * same one turns up twice, once live and once in a SYNC.
 *
 * @param expiresAt absolute epoch ms, 0 for permanent
 */
public record BanPayload(@NotNull String banId,
                         @Nullable UUID uuid,
                         @NotNull String playerName,
                         @NotNull String kickReason,
                         long expiresAt,
                         long issuedAt) {

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    public byte[] encode() {
        return PayloadIo.write(this::writeTo);
    }

    public static @Nullable BanPayload decode(byte @NotNull [] payload) {
        return PayloadIo.read(payload, BanPayload::readFrom);
    }

    // shared with SyncPayload, which packs a pile of these
    static BanPayload readFrom(DataInputStream in) throws IOException {
        return new BanPayload(
                in.readUTF(),
                PayloadIo.readUuid(in),
                in.readUTF(),
                in.readUTF(),
                in.readLong(),
                in.readLong()
        );
    }

    void writeTo(DataOutputStream out) throws IOException {
        out.writeUTF(banId);
        PayloadIo.writeUuid(out, uuid);
        out.writeUTF(playerName);
        out.writeUTF(kickReason);
        out.writeLong(expiresAt);
        out.writeLong(issuedAt);
    }
}
