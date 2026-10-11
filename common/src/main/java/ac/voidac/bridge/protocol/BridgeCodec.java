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
            throw new IllegalStateException("Failed to encode bridge frame", impossible);
        }

        byte[] signedRegion = buffer.toByteArray();
        byte[] mac = mac(signedRegion);

        byte[] frame = Arrays.copyOf(signedRegion, signedRegion.length + mac.length);
        System.arraycopy(mac, 0, frame, signedRegion.length, mac.length);
        return frame;
    }

    public @Nullable BridgeFrame decode(byte @Nullable [] frame) {
        if (frame == null || frame.length < BridgeProtocol.MAC_LENGTH + 16) return null;

        int signedLength = frame.length - BridgeProtocol.MAC_LENGTH;

        if (frame[0] != BridgeProtocol.MAGIC_0
                || frame[1] != BridgeProtocol.MAGIC_1
                || frame[2] != BridgeProtocol.VERSION) {
            return null;
        }

        byte[] expected = mac(Arrays.copyOf(frame, signedLength));
        byte[] actual = Arrays.copyOfRange(frame, signedLength, frame.length);
        if (!MessageDigest.isEqual(expected, actual)) return null;

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

            if (!replayGuard.accept(nonce, timestamp)) return null;

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
