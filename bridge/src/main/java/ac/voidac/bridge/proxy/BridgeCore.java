package ac.voidac.bridge.proxy;

import ac.voidac.bridge.protocol.BridgeCodec;
import ac.voidac.bridge.protocol.BridgeFrame;
import ac.voidac.bridge.protocol.MessageType;
import ac.voidac.bridge.protocol.payload.BanPayload;
import ac.voidac.bridge.protocol.payload.HelloPayload;
import ac.voidac.bridge.protocol.payload.SyncPayload;
import ac.voidac.bridge.protocol.payload.UnbanPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the bridge does, minus the bits that care whether this is Velocity
 * or Bungee. The proxy owns the ban list, decides who hears what, and is what
 * actually refuses a banned player. Backends get told over their own socket, so
 * whether anyone is playing on them has nothing to do with whether they hear us.
 */
public final class BridgeCore {

    private final ProxyPlatform platform;
    private final BridgeConfig config;
    private final BridgeCodec codec;
    private final BanRegistry bans;
    private final BridgeServer server;

    private static final String PROXY_ORIGIN = "proxy";

    public BridgeCore(@NotNull ProxyPlatform platform, @NotNull BridgeConfig config, @NotNull BanRegistry bans) {
        this.platform = platform;
        this.config = config;
        this.bans = bans;
        this.codec = new BridgeCodec(config.secret());
        this.server = new BridgeServer(platform, codec, (origin, frame) -> handleFrame(frame));
    }

    public void start() {
        server.start(config.bindHost(), config.port());
    }

    public void stop() {
        server.stop();
    }

    public @NotNull BanRegistry bans() {
        return bans;
    }

    public @NotNull BridgeConfig config() {
        return config;
    }

    /** Backends currently connected, for status output. */
    public @NotNull Set<String> connectedBackends() {
        return server.connected();
    }

    /** Already verified by the codec before it gets here. */
    private void handleFrame(@NotNull BridgeFrame frame) {
        switch (frame.type()) {
            case HELLO -> handleHello(frame); // ack like
            case ALERT -> forward(frame, false);
            case BAN -> handleBan(frame);
            case UNBAN -> handleUnban(frame);
            // we're the only thing that sends SYNC. getting one means a backend
            // is misconfigured, or something is pretending to be us
            case SYNC -> { }
        }
    }

    private void handleHello(@NotNull BridgeFrame frame) {
        HelloPayload hello = HelloPayload.decode(frame.payload());
        String version = hello != null ? hello.voidVersion() : "unknown";
        platform.info("Backend '" + frame.origin() + "' joined the bridge (Void " + version + ").");

        // the whole list every time, so a server that was down can't stay out of
        // step either way: it picks up what it missed and drops what we lifted
        List<BanPayload> payloads = bans.activePayloads(frame.origin());
        byte[] sync = codec.encode(MessageType.SYNC, PROXY_ORIGIN, new SyncPayload(payloads).encode());
        if (server.send(frame.origin(), sync) && !payloads.isEmpty()) {
            platform.info("Sent " + payloads.size() + " active ban(s) to '" + frame.origin() + "'.");
        }
    }

    private void handleBan(@NotNull BridgeFrame frame) {
        BanPayload ban = BanPayload.decode(frame.payload());
        if (ban == null || ban.isExpired()) return;

        if (!config.shares(frame.origin(), true)) return;

        Set<String> scope = normalise(config.scopeOf(frame.origin(), true));
        BanRegistry.Entry entry = new BanRegistry.Entry(
                ban.banId(), ban.uuid(), ban.playerName(), ban.kickReason(),
                ban.expiresAt(), ban.issuedAt(), frame.origin(), scope);

        if (!bans.put(entry)) return;

        platform.info("Ban " + ban.banId() + " for " + ban.playerName()
                + " from '" + frame.origin() + "' now applies to "
                + (scope.isEmpty() ? "the whole network" : scope.toString()) + ".");

        forward(frame, true);

        // only boot them outright if there's nowhere left to go. banned from one
        // group just means bounced off those servers, ServerPreConnect handles it
        if (ban.uuid() != null && bans.isNetworkWide(entry, platform.serverNames())) {
            platform.disconnect(ban.uuid(), config.banScreenPrefix() + ban.kickReason());
        }
    }

    private void handleUnban(@NotNull BridgeFrame frame) {
        UnbanPayload unban = UnbanPayload.decode(frame.payload());
        if (unban == null) return;
        if (!config.shares(frame.origin(), true)) return;

        if (bans.remove(unban.uuid(), unban.playerName())) {
            platform.info("Unbanned " + unban.playerName() + " network-wide (from '" + frame.origin() + "').");
        }
        forward(frame, true);
    }

    /**
     * Re-signs and sends on to everyone else in scope.
     * Re-encoding rather than relaying raw bytes gives each hop a fresh timestamp
     * and nonce, so nothing lands already stale. Origin is kept, which is how the
     * sender spots and drops its own message.
     */
    private void forward(@NotNull BridgeFrame frame, boolean forBans) {
        if (!config.shares(frame.origin(), forBans)) return;

        Collection<String> known = platform.serverNames();
        Set<String> scope = config.scopeOf(frame.origin(), forBans);
        Collection<String> targets = scope.isEmpty() ? known : scope;

        byte[] out = codec.encode(frame.type(), frame.origin(), frame.payload());
        for (String target : targets) {
            if (target.equalsIgnoreCase(frame.origin())) continue;
            // a backend that's offline gets the truth from its next SYNC
            server.send(target.toLowerCase(Locale.ROOT), out);
        }
    }

    /** Pass a null serverName to ask whether any ban applies at all. */
    public @Nullable BanRegistry.Entry banFor(@Nullable UUID uuid, @Nullable String playerName,
                                              @Nullable String serverName) {
        return bans.find(uuid, playerName, serverName);
    }

    /** True when there's nowhere left to go, so they get a real ban screen instead of a failed connect. */
    public boolean shouldDenyLogin(@NotNull BanRegistry.Entry entry) {
        return config.enforceAtLogin() && bans.isNetworkWide(entry, platform.serverNames());
    }

    public @NotNull String banScreen(@NotNull BanRegistry.Entry entry) {
        return config.banScreenPrefix() + entry.reason();
    }

    private static Set<String> normalise(Set<String> servers) {
        Set<String> out = new LinkedHashSet<>();
        for (String server : servers) {
            out.add(server.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
