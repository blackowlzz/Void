package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Length-prefixed framing for the bridge socket.
 * TCP is a stream and does not care where your messages start, so every frame
 * goes out as a 4 byte length followed by the signed frame itself.
 */
public final class FrameStream {

    private FrameStream() {
    }

    /** Anything bigger than this is a corrupt prefix, not a real frame. */
    private static final int MAX_FRAME = 16 * 1024 * 1024;

    public static void write(@NotNull OutputStream out, byte @NotNull [] frame) throws IOException {
        if (frame.length > MAX_FRAME) {
            throw new IOException("Frame too large to send: " + frame.length);
        }
        byte[] header = {
                (byte) (frame.length >>> 24),
                (byte) (frame.length >>> 16),
                (byte) (frame.length >>> 8),
                (byte) frame.length
        };
        // one write so two threads can't interleave a header and a body
        synchronized (out) {
            out.write(header);
            out.write(frame);
            out.flush();
        }
    }

    /**
     * Reads one frame.
     *
     * @return the frame, or {@code null} if the peer hung up cleanly
     * @throws IOException on a broken or nonsense stream, which the caller should
     *                     treat as "drop this connection"
     */
    public static byte @Nullable [] read(@NotNull DataInputStream in) throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (EOFException hungUp) {
            return null;
        }
        if (length <= 0 || length > MAX_FRAME) {
            throw new IOException("Implausible frame length: " + length);
        }
        byte[] frame = new byte[length];
        in.readFully(frame);
        return frame;
    }
}
