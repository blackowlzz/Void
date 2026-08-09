package ac.voidac.bridge.protocol.payload;

import ac.voidac.bridge.protocol.BridgeProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Boring serialisation plumbing the payloads share. */
final class PayloadIo {

    private PayloadIo() {
    }

    static byte[] write(@NotNull Writer body) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            body.write(out);
        } catch (IOException impossible) {
            throw new IllegalStateException("Failed to encode bridge payload", impossible);
        }
        return buffer.toByteArray();
    }

    /** Null means truncated or unreadable, callers just drop it. */
    static <T> @Nullable T read(byte @NotNull [] payload, @NotNull Reader<T> body) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            return body.read(in);
        } catch (IOException | IllegalArgumentException malformed) {
            return null;
        }
    }

    static void writeUuid(@NotNull DataOutputStream out, @Nullable UUID uuid) throws IOException {
        UUID value = uuid != null ? uuid : BridgeProtocol.UNKNOWN_UUID;
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    /** Nil UUID comes back as null. */
    static @Nullable UUID readUuid(@NotNull DataInputStream in) throws IOException {
        long most = in.readLong();
        long least = in.readLong();
        if (most == 0L && least == 0L) return null;
        return new UUID(most, least);
    }

    @FunctionalInterface
    interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }
}
