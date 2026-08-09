package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Frames bridge messages and signs them HMAC-SHA256.
 *
 * The port is open to whoever finds it, so this is the only thing standing
 * between a stranger and "ban everybody", and the proxy going yes chef. Header
 * is in the tag too, otherwise you could swap the type or the origin and still
 * verify.
 *
 * Layout: magic, version, type, timestamp, nonce, origin, payload, tag.
 */
public final class BridgeCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final SecretKeySpec key;
    private final ReplayGuard replayGuard = new ReplayGuard();

    public BridgeCodec(@NotNull String sharedSecret) {
        if (sharedSecret.isBlank()) {
            throw new IllegalArgumentException("Bridge secret must not be blank");
        }
        this.key = new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    /**
     * Builds a signed frame.
     *
     * @param origin who is sending, used for display and so the proxy doesn't echo it back
     */
    public byte @NotNull [] encode(@NotNull MessageType type, @NotNull String origin, byte @NotNull [] payload) {
        if (payload.length > BridgeProtocol.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Bridge payload too large: " + payload.length);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(payload.length + 96);
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(BridgeProtocol.MAGIC_0);
            out.writeByte(BridgeProtocol.MAGIC_1);
            out.writeByte(BridgeProtocol.VERSION);
            out.writeByte(type.id());
            out.writeLong(System.currentTimeMillis());
            out.writeUTF(newNonce());
            out.writeUTF(origin);
            out.writeInt(payload.length);
            out.write(payload);
        } catch (IOException impossible) {
            // it's a byte array. if this throws, the JVM has bigger problems than us
            throw new IllegalStateException("Failed to encode bridge frame", impossible);
        }

        byte[] signedRegion = buffer.toByteArray();
        byte[] mac = mac(signedRegion);

        byte[] frame = Arrays.copyOf(signedRegion, signedRegion.length + mac.length);
        System.arraycopy(mac, 0, frame, signedRegion.length, mac.length);
        return frame;
    }

    /**
     * Verifies and parses a frame.
     * Null means "not ours, or not signed with our key". Bad frames are normal
     * on a shared channel, so nothing throws.
     */
    public @Nullable BridgeFrame decode(byte @Nullable [] frame) {
        if (frame == null || frame.length < BridgeProtocol.MAC_LENGTH + 16) return null;

        int signedLength = frame.length - BridgeProtocol.MAC_LENGTH;

        // cheap non-secret checks first, no point doing crypto on obvious junk
        if (frame[0] != BridgeProtocol.MAGIC_0
                || frame[1] != BridgeProtocol.MAGIC_1
                || frame[2] != BridgeProtocol.VERSION) {
            return null;
        }

        byte[] expected = mac(Arrays.copyOf(frame, signedLength));
        byte[] actual = Arrays.copyOfRange(frame, signedLength, frame.length);
        // constant time, a timing oracle here leaks the tag one byte at a time
        if (!MessageDigest.isEqual(expected, actual)) return null;

        // signature is good, but a buggy peer can send nonsense just as well as
        // a hostile one, so keep parsing defensively
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame, 3, signedLength - 3))) {
            MessageType type = MessageType.byId(in.readByte());
            long timestamp = in.readLong();
            String nonce = in.readUTF();
            String origin = in.readUTF();
            int payloadLength = in.readInt();

            if (payloadLength < 0 || payloadLength > BridgeProtocol.MAX_PAYLOAD_BYTES
                    || payloadLength > in.available()) {
                return null;
            }

            if (Math.abs(System.currentTimeMillis() - timestamp) > BridgeProtocol.MAX_CLOCK_SKEW_MS) return null;

            // after signature and freshness, so garbage can't pollute the nonce cache
            if (!replayGuard.accept(nonce, timestamp)) return null;

            // unknown type from a newer Void is fine, just not for us
            if (type == null) return null;

            byte[] payload = new byte[payloadLength];
            in.readFully(payload);
            return new BridgeFrame(type, origin, timestamp, payload);
        } catch (IOException truncated) {
            return null;
        }
    }

    private byte[] mac(byte[] data) {
        try {
            // Mac isn't thread safe and frames come off netty and the main thread.
            // A fresh instance costs microseconds and kills the whole question.
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String newNonce() {
        byte[] raw = new byte[12];
        RANDOM.nextBytes(raw);
        char[] hex = new char[raw.length * 2];
        for (int i = 0; i < raw.length; i++) {
            hex[i * 2] = HEX[(raw[i] >> 4) & 0xF];
            hex[i * 2 + 1] = HEX[raw[i] & 0xF];
        }
        return new String(hex);
    }
}
