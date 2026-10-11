package ac.voidac.bridge.proxy;

import ac.voidac.bridge.protocol.BridgeCodec;
import ac.voidac.bridge.protocol.BridgeFrame;
import ac.voidac.bridge.protocol.FrameStream;
import ac.voidac.bridge.protocol.MessageType;
import ac.voidac.bridge.protocol.SocketTuning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class BridgeServer {
    private final ProxyPlatform platform;
    private final BridgeCodec codec;
    private final BiConsumer<String, BridgeFrame> onFrame;

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    private volatile @Nullable ServerSocket listener;
    private volatile boolean shuttingDown;

    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;

    private static final int MAX_CONNECTIONS = 256;

    private static final int HANDSHAKE_MAX_FRAME = 8 * 1024;

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

    public @NotNull Set<String> connected() {
        return connections.keySet();
    }

    public boolean send(@NotNull String serverName, byte @NotNull [] frame) {
        Connection connection = connections.get(serverName.toLowerCase(Locale.ROOT));
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
                if (openConnections.incrementAndGet() > MAX_CONNECTIONS) {
                    openConnections.decrementAndGet();
                    closeQuietly(client);
                    continue;
                }
                try {
                    Thread handler = new Thread(() -> handle(client), "void-bridge-conn");
                    handler.setDaemon(true);
                    handler.start();
                } catch (RuntimeException | Error startFailure) {
                    openConnections.decrementAndGet();
                    closeQuietly(client);
                    throw startFailure;
                }
            } catch (Exception e) {
                if (!shuttingDown) {
                    platform.warn("Bridge accept loop hiccuped: " + e.getMessage(), null);
                }
            }
        }
    }

    private void handle(@NotNull Socket client) {
        String serverName = null;
        try {
            client.setTcpNoDelay(true);
            SocketTuning.keepAlive(client);
            client.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

            DataInputStream in = new DataInputStream(client.getInputStream());

            byte[] first = FrameStream.read(in, HANDSHAKE_MAX_FRAME);
            BridgeFrame hello = first == null ? null : codec.decode(first);
            if (hello == null || hello.type() != MessageType.HELLO) {
                closeQuietly(client);
                return;
            }

            serverName = hello.origin().toLowerCase(Locale.ROOT);
            client.setSoTimeout(0);

            Connection previous = connections.put(serverName, new Connection(serverName, client));
            if (previous != null) closeQuietly(previous.socket());

            dispatch(serverName, hello);

            while (!shuttingDown && !client.isClosed()) {
                byte[] raw = FrameStream.read(in);
                if (raw == null) break;

                BridgeFrame frame = codec.decode(raw);
                if (frame == null) continue;
                dispatch(serverName, frame);
            }
        } catch (Exception e) {
        } finally {
            openConnections.decrementAndGet();
            if (serverName != null) {
                boolean wasOurs = connections.computeIfPresent(
                        serverName, (k, v) -> v.socket() == client ? null : v) == null;
                if (wasOurs) {
                    platform.info("Backend '" + serverName + "' disconnected from the bridge.");
                }
            }
            closeQuietly(client);
        }
    }

    private void dispatch(@NotNull String serverName, @NotNull BridgeFrame frame) {
        try {
            onFrame.accept(serverName, frame);
        } catch (RuntimeException failure) {
            platform.warn("Could not handle a " + frame.type() + " frame from '" + serverName + "'", failure);
        }
    }

    private static void closeQuietly(@NotNull java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
