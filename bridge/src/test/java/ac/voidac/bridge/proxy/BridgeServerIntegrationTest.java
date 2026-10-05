package ac.voidac.bridge.proxy;

import ac.voidac.bridge.protocol.BridgeCodec;
import ac.voidac.bridge.protocol.BridgeFrame;
import ac.voidac.bridge.protocol.FrameStream;
import ac.voidac.bridge.protocol.MessageType;
import ac.voidac.bridge.protocol.payload.BanPayload;
import ac.voidac.bridge.protocol.payload.HelloPayload;
import ac.voidac.bridge.protocol.payload.SyncPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeServerIntegrationTest {

    private static final String SECRET = "integration-secret-0123456789abcdef";

    @TempDir
    Path directory;

    private RecordingPlatform platform;
    private BanRegistry registry;
    private BridgeCore core;
    private BridgeCodec codec;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = probe.getLocalPort();
        }
        Files.writeString(directory.resolve("config.yml"),
                "secret: \"" + SECRET + "\"\nport: " + port + "\nbind: \"127.0.0.1\"\n");

        platform = new RecordingPlatform(directory, "survival", "creative");
        BridgeConfig config = BridgeConfig.load(directory, platform);
        assertNotNull(config);
        registry = new BanRegistry(directory, platform);
        core = new BridgeCore(platform, config, registry);
        codec = new BridgeCodec(SECRET);
    }

    @AfterEach
    void tearDown() {
        core.stop();
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(5_000);
        return socket;
    }

    private void send(Socket socket, MessageType type, String origin, byte[] payload) throws IOException {
        FrameStream.write(socket.getOutputStream(), codec.encode(type, origin, payload));
    }

    private BridgeFrame receive(Socket socket) throws IOException {
        byte[] raw = FrameStream.read(new DataInputStream(socket.getInputStream()));
        assertNotNull(raw);
        return codec.decode(raw);
    }

    private static byte[] ban(String id, UUID uuid, String name) {
        return new BanPayload(id, uuid, name, "cheating", 0L, System.currentTimeMillis()).encode();
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("condition not met in time");
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }

    @Test
    void helloIsAnsweredWithTheCurrentBanList() throws IOException {
        UUID existing = UUID.randomUUID();
        registry.put(new BanRegistry.Entry("VOID-000001", existing, "Alex", "cheating", 0L, 1L, "lobby", Set.of()));
        core.start();

        try (Socket socket = connect()) {
            send(socket, MessageType.HELLO, "survival", new HelloPayload("test").encode());

            BridgeFrame frame = receive(socket);
            assertEquals(MessageType.SYNC, frame.type());
            SyncPayload sync = SyncPayload.decode(frame.payload());
            assertNotNull(sync);
            assertEquals(1, sync.bans().size());
            assertEquals(existing, sync.bans().get(0).uuid());
        }
    }

    @Test
    void banFromTheConnectedBackendIsStoredAndSpoofedOnesAreIgnored() throws IOException {
        core.start();
        UUID real = UUID.randomUUID();
        UUID spoofed = UUID.randomUUID();

        try (Socket socket = connect()) {
            send(socket, MessageType.HELLO, "survival", new HelloPayload("test").encode());
            receive(socket);

            send(socket, MessageType.BAN, "creative", ban("VOID-000002", spoofed, "Spoof"));
            send(socket, MessageType.BAN, "survival", ban("VOID-000003", real, "Real"));

            await(() -> registry.find(real, "Real", null) != null);
            assertNull(registry.find(spoofed, "Spoof", null));
            assertTrue(core.connectedBackends().contains("survival"));
        }
    }

    @Test
    void aFailingHandlerDoesNotDropTheConnection() throws IOException {
        core.start();
        platform.failOnDisconnect = true;
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        try (Socket socket = connect()) {
            send(socket, MessageType.HELLO, "survival", new HelloPayload("test").encode());
            receive(socket);

            send(socket, MessageType.BAN, "survival", ban("VOID-000004", first, "First"));
            await(() -> platform.warnings.stream().anyMatch(message -> message.contains("Could not handle")));

            platform.failOnDisconnect = false;
            send(socket, MessageType.BAN, "survival", ban("VOID-000005", second, "Second"));

            await(() -> registry.find(second, "Second", null) != null);
            assertNotNull(registry.find(first, "First", null));
            assertTrue(core.connectedBackends().contains("survival"));
        }
    }

    @Test
    void aFrameSignedWithAnotherSecretNeverGetsAHandshake() throws IOException {
        core.start();
        BridgeCodec stranger = new BridgeCodec("some-other-secret-0123456789abcdef");

        try (Socket socket = connect()) {
            FrameStream.write(socket.getOutputStream(),
                    stranger.encode(MessageType.HELLO, "survival", new HelloPayload("evil").encode()));

            assertNull(FrameStream.read(new DataInputStream(socket.getInputStream())));
        }
        assertFalse(core.connectedBackends().contains("survival"));
    }
}
