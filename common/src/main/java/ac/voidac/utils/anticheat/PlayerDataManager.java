package ac.voidac.utils.anticheat;

import ac.voidac.VoidAPI;
import ac.voidac.api.event.events.VoidJoinEvent;
import ac.voidac.api.event.events.VoidQuitEvent;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.reflection.GeyserUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    // Holder — PlayerDataManager is constructed inside VoidAPI's ctor, so a
    // plain static-final would see a null VoidAPI.INSTANCE. Holder init runs
    // on first fire, after VoidAPI is fully built.
    private static final class Channels {
        static final VoidJoinEvent.Channel JOIN = VoidAPI.INSTANCE.getEventBus().get(VoidJoinEvent.class);
        static final VoidQuitEvent.Channel QUIT = VoidAPI.INSTANCE.getEventBus().get(VoidQuitEvent.class);
    }

    public final Collection<User> exemptUsers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<User, VoidPlayer> playerDataMap = new ConcurrentHashMap<>();

    @Nullable
    public VoidPlayer getPlayer(final @NotNull UUID uuid) {
        // Fast path: PacketEvents channel lookup (works in online mode).
        try {
            Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(uuid);
            if (channel != null) {
                User user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
                VoidPlayer player = getPlayer(user);
                if (player != null) return player;
            }
        } catch (Exception ignored) {}

        // Fallback: linear scan by platform UUID.
        // Required in offline mode where PacketEvents may not index the channel
        // under the offline UUID that Bukkit's Player#getUniqueId() returns.
        for (VoidPlayer p : playerDataMap.values()) {
            if (p.platformPlayer != null && uuid.equals(p.platformPlayer.getUniqueId())) {
                if (p.platformPlayer.isExternalPlayer()) return null;
                return p;
            }
        }
        return null;
    }

    @Nullable
    public VoidPlayer getPlayer(final @NotNull User user) {
        @Nullable VoidPlayer player = playerDataMap.get(user);
        if (player != null && player.platformPlayer != null && player.platformPlayer.isExternalPlayer())
            return null;
        return player;
    }

    public boolean shouldCheck(@NotNull User user) {
        if (exemptUsers.contains(user)) return false;
        if (!ChannelHelper.isOpen(user.getChannel())) return false;

        if (user.getUUID() != null) {
            // Bedrock players don't have Java movement
            if (GeyserUtil.isBedrockPlayer(user.getUUID())) {
                exemptUsers.add(user);
                return false;
            }

            // Has exempt permission
            VoidPlayer voidPlayer = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
            if (voidPlayer != null && voidPlayer.hasPermission("void.exempt")) {
                exemptUsers.add(user);
                return false;
            }

            // Geyser formatted player string
            // This will never happen for Java players, as the first character in the 3rd group is always 4 (xxxxxxxx-xxxx-4xxx-xxxx-xxxxxxxxxxxx)
            if (user.getUUID().toString().startsWith("00000000-0000-0000-0009")) {
                exemptUsers.add(user);
                return false;
            }
        }

        return true;
    }

    public void addUser(final @NotNull User user) {
        if (shouldCheck(user)) {
            VoidPlayer player = new VoidPlayer(user);
            playerDataMap.put(user, player);
            Channels.JOIN.fire(player);
        }
    }

    public VoidPlayer remove(final @NotNull User user) {
        return playerDataMap.remove(user);
    }

    public void onDisconnect(User user) {
        VoidPlayer voidPlayer = remove(user);
        if (voidPlayer != null) Channels.QUIT.fire(voidPlayer);
        if (voidPlayer != null) voidPlayer.storageEspDecoyManager.shutdown();
        exemptUsers.remove(user);

        UUID uuid = user.getProfile().getUUID();

        // All cleanup paths should call onDisconnect; routing the session-close + toggle
        // eviction here means a stuck PE event (or a JVM-level channel
        // close that doesn't surface as UserDisconnectEvent) doesn't leak an open session.
        // hooks/toggles are NOOP when the datastore is disabled or its init failed
        // AND go NOOP mid-session if an operator runs /void reload after flipping database.enabled to false
        // a player who joined under the prior (enabled) config and disconnects post-reload has no live writer to fire onQuit, so their session stays open (row closed_at IS NULL).
        // The next datastore-enabled boot's crash sweep stamps closed_at = last_activity for still-open rows; permanently-disabled-after-the-fact leaves the row untouched until DB is enabled again.
        VoidAPI.INSTANCE.getDataStoreLifecycle().liveWriteHooks()
                .onQuitFromUserDisconnect(user, voidPlayer, System.currentTimeMillis());
        if (uuid != null) {
            VoidAPI.INSTANCE.getDataStoreLifecycle().playerToggleStore().evict(uuid);
        }

        // Check if calling async is safe
        if (uuid == null)
            return; // folia doesn't like null getPlayer()

        VoidAPI.INSTANCE.getAlertManager().handlePlayerQuit(
                VoidAPI.INSTANCE.getPlatformPlayerFactory().getFromUUID(uuid)
        );

        VoidAPI.INSTANCE.getSpectateManager().onQuit(uuid);

        // TODO (Cross-platform) confirm this is 100% correct and will always remove players from cache when necessary
        VoidAPI.INSTANCE.getPlatformPlayerFactory().invalidatePlayer(uuid);
    }

    public Collection<VoidPlayer> getEntries() {
        return playerDataMap.values();
    }

    public int size() {
        return playerDataMap.size();
    }
}
