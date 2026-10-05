package ac.voidac.bridge.protocol;

import jdk.net.ExtendedSocketOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocketTuningTest {

    @Test
    void keepAliveIsEnabledAndShortenedWhereTheSystemAllowsIt() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
             Socket accepted = server.accept()) {

            SocketTuning.keepAlive(client);
            SocketTuning.keepAlive(accepted);

            assertTrue(client.getKeepAlive());
            assertTrue(accepted.getKeepAlive());
            if (client.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
                assertEquals(30, client.getOption(ExtendedSocketOptions.TCP_KEEPIDLE));
                assertEquals(10, client.getOption(ExtendedSocketOptions.TCP_KEEPINTERVAL));
                assertEquals(3, client.getOption(ExtendedSocketOptions.TCP_KEEPCOUNT));
            }
        }
    }
}
