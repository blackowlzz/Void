package ac.voidac.bridge.protocol;

import jdk.net.ExtendedSocketOptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketOption;

public final class SocketTuning {

    private static final int KEEPALIVE_IDLE_SECONDS = 30;
    private static final int KEEPALIVE_INTERVAL_SECONDS = 10;
    private static final int KEEPALIVE_PROBES = 3;

    private SocketTuning() {
    }

    public static void keepAlive(@NotNull Socket socket) throws SocketException {
        socket.setKeepAlive(true);
        tune(socket, ExtendedSocketOptions.TCP_KEEPIDLE, KEEPALIVE_IDLE_SECONDS);
        tune(socket, ExtendedSocketOptions.TCP_KEEPINTERVAL, KEEPALIVE_INTERVAL_SECONDS);
        tune(socket, ExtendedSocketOptions.TCP_KEEPCOUNT, KEEPALIVE_PROBES);
    }

    private static void tune(Socket socket, SocketOption<Integer> option, int value) {
        try {
            if (socket.supportedOptions().contains(option)) socket.setOption(option, value);
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
