package ac.voidac.bridge.proxy;

import ac.voidac.bridge.protocol.BridgeCodec;
import ac.voidac.bridge.protocol.BridgeFrame;
import ac.voidac.bridge.protocol.FrameStream;
import ac.voidac.bridge.protocol.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Listens for backends and hangs onto their sockets. They stay connected with
 * nobody playing on them, which is the whole point.
 *
 * First frame has to be a HELLO that verifies or the connection is gone before
 * it gets to say anything else. Port scanners need not apply.
 */
public final class BridgeServer {

    private final ProxyPlatform platform;
    private final BridgeCodec codec;
    private final BiConsumer<String, BridgeFrame> onFrame;

    /** server name to its live connection. */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    private volatile @Nullable ServerSocket listener;
    private volatile boolean shuttingDown;

    /** A backend that connects and then says nothing is not worth a thread. */
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;

    /** Plenty for any network, and a hard stop on somebody opening sockets for fun. */
    private static final int MAX_CONNECTIONS = 256;

    private final AtomicInteger openConnections = new AtomicInteger();

    private record Connection(String serverName, Socket socket) {
    }

    public BridgeServer(@NotNull ProxyPlatform platform, @NotNull BridgeCodec codec,
                        @NotNull BiConsumer<String, BridgeFrame> onFrame) {
        this.platform = platform;
        this.codec = codec;
        this.onFrame = onFrame;
    }

    public void start(@NotNull String bindHost, int port) {
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(bindHost, port));
            this.listener = socket;
        } catch (Exception e) {
            platform.warn("Could not listen on " + bindHost + ":" + port
                    + ". The bridge is off; pick a free port in config.yml.", e);
            return;
        }

        Thread accept = new Thread(this::acceptLoop, "void-bridge-accept");
        accept.setDaemon(true);
        accept.start();
        platform.info("Bridge listening on " + bindHost + ":" + port + ".");
    }

    public void stop() {
        shuttingDown = true;
        ServerSocket socket = listener;
        if (socket != null) closeQuietly(socket);
        for (Connection connection : connections.values()) {
            closeQuietly(connection.socket());
        }
        connections.clear();
    }

    /** Names of backends currently connected. */
    public @NotNull java.util.Set<String> connected() {
        return connections.keySet();
    }

    /**
     * Sends a frame to one backend.
     *
     * @return false if that backend isn't connected. Nothing to queue for: it
     *         gets the full picture from the SYNC on its next connect.
     */
    public boolean send(@NotNull String serverName, byte @NotNull [] frame) {
        Connection connection = connections.get(serverName.toLowerCase(java.util.Locale.ROOT));
        if (connection == null) return false;
        try {
            FrameStream.write(connection.socket().getOutputStream(), frame);
            return true;
        } catch (Exception e) {
            closeQuietly(connection.socket());
            return false;
        }
    }

    private void acceptLoop() {
        ServerSocket socket = listener;
        while (!shuttingDown && socket != null && !socket.isClosed()) {
            try {
                Socket client = socket.accept();
                if (openConnections.get() >= MAX_CONNECTIONS) {
                    closeQuietly(client);
                    continue;
                }
                Thread handler = new Thread(() -> handle(client), "void-bridge-conn");
                handler.setDaemon(true);
                handler.start();
            } catch (Exception e) {
                if (!shuttingDown) {
                    platform.warn("Bridge accept loop hiccuped: " + e.getMessage(), null);
                }
            }
        }
    }

    private void handle(@NotNull Socket client) {
        openConnections.incrementAndGet();
        String serverName = null;
        try {
            client.setTcpNoDelay(true);
            client.setKeepAlive(true);
            // only for the handshake: after that a quiet backend is a normal backend
            client.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

            DataInputStream in = new DataInputStream(client.getInputStream());

            byte[] first = FrameStream.read(in);
            BridgeFrame hello = first == null ? null : codec.decode(first);
            if (hello == null || hello.type() != MessageType.HELLO) {
                // wrong key, wrong protocol, or somebody poking the port
                closeQuietly(client);
                return;
            }

            serverName = hello.origin().toLowerCase(java.util.Locale.ROOT);
            client.setSoTimeout(0);

            Connection previous = connections.put(serverName, new Connection(serverName, client));
            if (previous != null) closeQuietly(previous.socket());

            onFrame.accept(serverName, hello);

            while (!shuttingDown && !client.isClosed()) {
                byte[] raw = FrameStream.read(in);
                if (raw == null) break;

                BridgeFrame frame = codec.decode(raw);
                if (frame == null) continue;
                onFrame.accept(serverName, frame);
            }
        } catch (Exception e) {
            // a backend restarting looks exactly like this, so keep it quiet
        } finally {
            openConnections.decrementAndGet();
            if (serverName != null) {
                // only drop the entry if it's still ours: a reconnect may have
                // already replaced us, and clearing that would strand the new one
                boolean wasOurs = connections.computeIfPresent(
                        serverName, (k, v) -> v.socket() == client ? null : v) == null;
                if (wasOurs) {
                    platform.info("Backend '" + serverName + "' disconnected from the bridge.");
                }
            }
            closeQuietly(client);
        }
    }

    private static void closeQuietly(@NotNull java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // already shut, which is where we wanted it
        }
    }
}
