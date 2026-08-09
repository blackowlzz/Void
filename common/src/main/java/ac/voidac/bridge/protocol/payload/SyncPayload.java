package ac.voidac.bridge.protocol.payload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The proxy's whole ban list, sent back on HELLO.
 * Receivers merge instead of replacing, so a purely local ban doesn't get
 * nuked by a sync.
 */
public record SyncPayload(@NotNull List<BanPayload> bans) {

    // an honest sync fits easily. a corrupt length prefix does not get to talk
    // us into preallocating a list with a billion slots
    private static final int MAX_ENTRIES = 200_000;

    public byte[] encode() {
        return PayloadIo.write(out -> {
            out.writeInt(bans.size());
            for (BanPayload ban : bans) {
                ban.writeTo(out);
            }
        });
    }

    public static @Nullable SyncPayload decode(byte @NotNull [] payload) {
        return PayloadIo.read(payload, in -> {
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IllegalArgumentException("Implausible sync size: " + count);
            }
            List<BanPayload> bans = new ArrayList<>(Math.min(count, 1024));
            for (int i = 0; i < count; i++) {
                bans.add(BanPayload.readFrom(in));
            }
            return new SyncPayload(bans);
        });
    }
}
