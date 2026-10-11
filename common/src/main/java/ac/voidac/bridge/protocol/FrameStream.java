package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

public final class FrameStream {
    private FrameStream() {
    }

    private static final int MAX_FRAME = 16 * 1024 * 1024;

    public static void write(@NotNull OutputStream out, byte @NotNull [] frame) throws IOException {
        if (frame.length > MAX_FRAME) {
            throw new IOException("Frame too large to send: " + frame.length);
        }
        byte[] message = new byte[Integer.BYTES + frame.length];
        message[0] = (byte) (frame.length >>> 24);
        message[1] = (byte) (frame.length >>> 16);
        message[2] = (byte) (frame.length >>> 8);
        message[3] = (byte) frame.length;
        System.arraycopy(frame, 0, message, Integer.BYTES, frame.length);
        synchronized (out) {
            out.write(message);
            out.flush();
        }
    }

    public static byte @Nullable [] read(@NotNull DataInputStream in) throws IOException {
        return read(in, MAX_FRAME);
    }

    public static byte @Nullable [] read(@NotNull DataInputStream in, int maxFrame) throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (EOFException hungUp) {
            return null;
        }
        if (length <= 0 || length > Math.min(maxFrame, MAX_FRAME)) {
            throw new IOException("Implausible frame length: " + length);
        }
        byte[] frame = new byte[length];
        in.readFully(frame);
        return frame;
    }
}
