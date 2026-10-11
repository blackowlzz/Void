package ac.voidac.bridge;

import ac.voidac.VoidAPI;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.bridge.protocol.BridgeCodec;
import ac.voidac.bridge.protocol.BridgeFrame;
import ac.voidac.bridge.protocol.FrameStream;
import ac.voidac.bridge.protocol.MessageType;
import ac.voidac.bridge.protocol.SocketTuning;
import ac.voidac.bridge.protocol.payload.AlertPayload;
import ac.voidac.bridge.protocol.payload.BanPayload;
import ac.voidac.bridge.protocol.payload.HelloPayload;
import ac.voidac.bridge.protocol.payload.SyncPayload;
import ac.voidac.bridge.protocol.payload.UnbanPayload;
import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.manager.init.stop.StoppableInitable;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BridgeClient implements StartableInitable, StoppableInitable {
    private volatile boolean enabled;
    private volatile @Nullable BridgeCodec codec;
    private volatile String serverName = "unknown";
    private volatile String proxyHost = "127.0.0.1";
    private volatile int proxyPort = 25599;

    private volatile boolean sendAlerts;
    private volatile boolean receiveAlerts;
    private volatile boolean sendBans;
    private volatile boolean receiveBans;
    private volatile boolean kickOnRemoteBan;
    private volatile boolean proxyAuthoritative;

    private volatile @Nullable Socket socket;
    private volatile boolean linked;
    private final BridgeOutbox outbox = new BridgeOutbox(this::transmit, MAX_PENDING, MAX_QUEUED_WRITES, "void-bridge-writer");
    private volatile @Nullable Thread worker;
    private volatile boolean shuttingDown;

    private volatile int generation;

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final long RETRY_MIN_MS = 1_000;
    private static final long RETRY_MAX_MS = 30_000;
    private static final int MAX_PENDING = 500;
    private static final int MAX_QUEUED_WRITES = 1_000;
    private static final long SHUTDOWN_DRAIN_MS = 2_000;
    private static final long DATABASE_WAIT_MS = 60_000;
    private static final long DATABASE_POLL_MS = 100;

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        outbox.shutdown(SHUTDOWN_DRAIN_MS);
        shuttingDown = true;
        disconnect();
        outbox.clear();
    }

    public void reload(@NotNull ConfigManager config) {
        generation++;
        disconnect();

        this.enabled = config.getBooleanElse("bridge.enabled", false);
        this.serverName = config.getStringElse("bridge.server-name", "unknown");
        this.proxyHost = config.getStringElse("bridge.proxy-host", "127.0.0.1");
        this.proxyPort = config.getIntElse("bridge.proxy-port", 25599);

        this.sendAlerts = config.getBooleanElse("bridge.alerts.send", true);
        this.receiveAlerts = config.getBooleanElse("bridge.alerts.receive", true);
        this.sendBans = config.getBooleanElse("bridge.bans.send", true);
        this.receiveBans = config.getBooleanElse("bridge.bans.receive", true);
        this.kickOnRemoteBan = config.getBooleanElse("bridge.bans.kick-on-remote-ban", true);
        this.proxyAuthoritative = config.getBooleanElse("bridge.bans.proxy-authoritative", true);

        this.codec = null;
        if (!enabled) {
            outbox.clear();
            return;
        }

        String secret = config.getStringElse("bridge.secret", "");
        if (secret.isBlank() || secret.equals("CHANGE-ME")) {
            LogUtil.warn("Bridge is enabled but bridge.secret is unset. Copy the secret from your "
                    + "proxy's void-bridge config.yml, then reload. Bridge stays off.");
            this.enabled = false;
            return;
        }
        if ("unknown".equals(serverName)) {
            LogUtil.warn("bridge.server-name is not set, alerts from here will just say 'unknown'.");
        }

        this.codec = new BridgeCodec(secret);
        startWorker(generation);
    }

    public boolean isEnabled() {
        return enabled && codec != null;
    }

    public @NotNull String getServerName() {
        return serverName;
    }

    public boolean isConnected() {
        Socket active = socket;
        return active != null && active.isConnected() && !active.isClosed();
    }

    public void sendAlert(@Nullable UUID playerUuid, @NotNull String playerName,
                          @NotNull String checkName, int violations, @NotNull String renderedMessage) {
        if (!isEnabled() || !sendAlerts) return;
        outbox.submit(MessageType.ALERT,
                new AlertPayload(playerUuid, playerName, checkName, violations, renderedMessage).encode());
    }

    public void sendBan(@NotNull String banId, @Nullable UUID uuid, @NotNull String playerName,
                        @NotNull String kickReason, long expiresAt, long issuedAt) {
        if (!isEnabled() || !sendBans) return;
        outbox.submit(MessageType.BAN,
                new BanPayload(banId, uuid, playerName, kickReason, expiresAt, issuedAt).encode());
    }

    public void sendUnban(@Nullable UUID uuid, @NotNull String playerName, @NotNull String banId) {
        if (!isEnabled() || !sendBans) return;
        outbox.submit(MessageType.UNBAN, new UnbanPayload(uuid, playerName, banId).encode());
    }

    private boolean transmit(@NotNull MessageType type, byte @NotNull [] payload) {
        BridgeCodec active = codec;
        Socket connection = socket;
        if (!linked || active == null || connection == null || connection.isClosed()) return false;

        try {
            OutputStream out = connection.getOutputStream();
            FrameStream.write(out, active.encode(type, serverName, payload));
            return true;
        } catch (Exception e) {
            LogUtil.warn("Bridge send failed (" + type + "), reconnecting: " + e.getMessage());
            closeQuietly(connection);
            return false;
        }
    }

    private void startWorker(int forGeneration) {
        Thread thread = new Thread(() -> runWorker(forGeneration), "void-bridge");
        thread.setDaemon(true);
        this.worker = thread;
        thread.start();
    }

    private void runWorker(int forGeneration) {
        long backoff = RETRY_MIN_MS;
        boolean alreadyComplained = false;

        while (!shuttingDown && forGeneration == generation && enabled) {
            Socket connection = new Socket();
            try {
                connection.connect(new InetSocketAddress(proxyHost, proxyPort), CONNECT_TIMEOUT_MS);
                connection.setTcpNoDelay(true);
                SocketTuning.keepAlive(connection);
                this.socket = connection;

                sayHello(connection);
                if (readUntilClosed(connection, forGeneration)) {
                    backoff = RETRY_MIN_MS;
                    alreadyComplained = false;
                } else if (!alreadyComplained) {
                    LogUtil.warn("Bridge reached " + proxyHost + ":" + proxyPort + " but the proxy hung up"
                            + " without answering. Check that proxy-port is the bridge port rather than the"
                            + " Minecraft one, and that the secret matches the proxy's.");
                    alreadyComplained = true;
                }
            } catch (Exception e) {
                if (!alreadyComplained) {
                    LogUtil.warn("Bridge can't reach the proxy at " + proxyHost + ":" + proxyPort
                            + " (" + e.getMessage() + "). Retrying quietly in the background.");
                    alreadyComplained = true;
                }
            } finally {
                if (this.socket == connection) {
                    this.linked = false;
                    this.socket = null;
                }
                closeQuietly(connection);
            }

            if (shuttingDown || forGeneration != generation || !enabled) return;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            backoff = Math.min(backoff * 2, RETRY_MAX_MS);
        }
    }

    private void sayHello(@NotNull Socket connection) throws Exception {
        BridgeCodec active = codec;
        if (active == null) return;
        String version = VoidAPI.INSTANCE.getVoidPlugin().getDescription().getVersion();
        FrameStream.write(connection.getOutputStream(),
                active.encode(MessageType.HELLO, serverName, new HelloPayload(version).encode()));
    }

    private boolean readUntilClosed(@NotNull Socket connection, int forGeneration) throws Exception {
        DataInputStream in = new DataInputStream(connection.getInputStream());
        boolean answered = false;

        while (!shuttingDown && forGeneration == generation) {
            byte[] raw = FrameStream.read(in);
            if (raw == null) {
                if (answered) LogUtil.warn("Bridge lost the proxy connection, reconnecting.");
                return answered;
            }
            if (answered) {
                handleIncoming(raw);
                continue;
            }
            answered = true;
            LogUtil.info("Bridge connected to the proxy at " + proxyHost + ":" + proxyPort
                    + " as '" + serverName + "'.");
            linked = true;
            handleIncoming(raw);
            outbox.flush();
        }
        return answered;
    }

    private void disconnect() {
        this.linked = false;
        Socket active = socket;
        if (active != null) closeQuietly(active);
        this.socket = null;

        Thread activeWorker = worker;
        if (activeWorker != null) activeWorker.interrupt();
        this.worker = null;
    }

    private static void closeQuietly(@NotNull Socket connection) {
        try {
            connection.close();
        } catch (Exception ignored) {
        }
    }

    private void handleIncoming(byte @NotNull [] raw) {
        BridgeCodec active = codec;
        if (!enabled || active == null) return;

        BridgeFrame frame = active.decode(raw);
        if (frame == null) return;

        if (frame.type() == MessageType.BAN || frame.type() == MessageType.UNBAN || frame.type() == MessageType.SYNC) {
            awaitPunishmentDatabase();
        }

        if (frame.origin().equals(serverName) && frame.type() != MessageType.SYNC) return;

        switch (frame.type()) {
            case ALERT -> handleAlert(frame);
            case BAN -> handleBan(frame);
            case UNBAN -> handleUnban(frame);
            case SYNC -> handleSync(frame);
            case HELLO -> { }
        }
    }

    private void awaitPunishmentDatabase() {
        long deadline = System.currentTimeMillis() + DATABASE_WAIT_MS;
        while (!shuttingDown && !VoidAPI.INSTANCE.getPunishmentDatabase().isReady()
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(DATABASE_POLL_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void handleAlert(@NotNull BridgeFrame frame) {
        if (!receiveAlerts || !VoidAPI.INSTANCE.getAlertManager().hasAlertListeners()) return;

        AlertPayload alert = AlertPayload.decode(frame.payload());
        if (alert == null) return;

        Component message = MessageUtil.miniMessage(alert.message());
        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> VoidAPI.INSTANCE.getAlertManager().sendAlert(message, null));
    }

    private void handleBan(@NotNull BridgeFrame frame) {
        if (!receiveBans) return;

        BanPayload ban = BanPayload.decode(frame.payload());
        if (ban == null || ban.isExpired()) return;

        boolean applied = VoidAPI.INSTANCE.getVoidBanManager().applyRemoteBan(ban);
        if (!applied || !kickOnRemoteBan) return;

        kickIfOnline(ban.uuid(), ban.playerName(), ban.kickReason());
    }

    private void handleUnban(@NotNull BridgeFrame frame) {
        if (!receiveBans) return;

        UnbanPayload unban = UnbanPayload.decode(frame.payload());
        if (unban == null) return;

        VoidAPI.INSTANCE.getVoidBanManager().applyRemoteUnban(unban.uuid(), unban.playerName());
    }

    private void handleSync(@NotNull BridgeFrame frame) {
        if (!receiveBans) return;

        SyncPayload sync = SyncPayload.decode(frame.payload());
        if (sync == null) return;

        int applied = 0;
        Map<UUID, String> live = new HashMap<>();
        for (BanPayload ban : sync.bans()) {
            if (ban.isExpired()) continue;
            if (ban.uuid() != null) live.put(ban.uuid(), ban.banId());
            if (VoidAPI.INSTANCE.getVoidBanManager().applyRemoteBan(ban)) applied++;
        }
        if (applied > 0) {
            LogUtil.info("Bridge sync: picked up " + applied + " ban(s) from while we were away.");
        }

        if (!proxyAuthoritative) return;
        int dropped = VoidAPI.INSTANCE.getVoidBanManager().reconcileWithProxy(live);
        if (dropped > 0) {
            LogUtil.info("Bridge sync: dropped " + dropped + " ban(s) the proxy no longer has.");
        }
    }

    private void kickIfOnline(@Nullable UUID uuid, @NotNull String playerName, @NotNull String reason) {
        PlatformPlayer target = uuid != null
                ? VoidAPI.INSTANCE.getPlatformPlayerFactory().getFromUUID(uuid)
                : null;
        if (target == null) {
            target = VoidAPI.INSTANCE.getPlatformPlayerFactory().getFromName(playerName);
        }
        if (target == null) return;

        final PlatformPlayer victim = target;
        VoidAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                victim, VoidAPI.INSTANCE.getVoidPlugin(),
                () -> victim.kickPlayer(reason), null, 0L);
    }
}
