package ac.voidac.manager;

import ac.voidac.VoidAPI;
import ac.voidac.bridge.protocol.payload.BanPayload;
import ac.voidac.manager.punishment.ActiveBanRecord;
import ac.voidac.manager.punishment.ProxyBanReconciler;
import ac.voidac.manager.punishment.PunishmentDatabase;
import ac.voidac.manager.punishment.PunishmentDuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Native timed-ban enforcement for Void.
 * Used as a fallback when LiteBans (or another ban plugin) is not installed.
 *
 * Bans are stored in the {@code void_active_bans} table of {@code punishments.db}.
 * Enforcement happens at login via {@code BukkitLoginListener} (AsyncPlayerPreLoginEvent).
 */
public class VoidBanManager {

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Records a ban.  The kick reason should already be fully formatted (color codes translated,
     * all placeholders resolved; it is shown verbatim on the disconnect screen.
     */
    public void ban(String banId, UUID uuid, String playerName, String kickReason, String durationStr, long timestamp) {
        long expiresAt = parseExpiry(durationStr);
        VoidAPI.INSTANCE.getPunishmentDatabase().activeBanInsert(banId, uuid, playerName, kickReason, expiresAt, timestamp);
        VoidAPI.INSTANCE.getBridgeClient().sendBan(banId, uuid, playerName, kickReason, expiresAt, timestamp);
    }

    /** Removes a ban by UUID.  Returns {@code true} if a ban was found and removed. */
    public boolean unban(UUID uuid) {
        ActiveBanRecord existing = VoidAPI.INSTANCE.getPunishmentDatabase().activeBanQuery(uuid);
        boolean removed = VoidAPI.INSTANCE.getPunishmentDatabase().activeBanRemove(uuid);
        if (removed) {
            forgetProxyBan(uuid);
            String name = existing != null ? existing.playerName() : uuid.toString();
            String banId = existing != null ? existing.banId() : "";
            VoidAPI.INSTANCE.getBridgeClient().sendUnban(uuid, name, banId);
        }
        return removed;
    }

    /**
     * Removes a ban by player name (case-insensitive).
     * Returns the UUID that was unbanned, or {@code null} if no entry was found.
     */
    public @Nullable UUID unbanByName(String name) {
        ActiveBanRecord existing = VoidAPI.INSTANCE.getPunishmentDatabase().activeBanQueryByName(name);
        UUID removed = VoidAPI.INSTANCE.getPunishmentDatabase().activeBanRemoveByName(name);
        if (removed != null) {
            forgetProxyBan(removed);
            VoidAPI.INSTANCE.getBridgeClient().sendUnban(removed, name, existing != null ? existing.banId() : "");
        }
        return removed;
    }

    private static void forgetProxyBan(UUID uuid) {
        if (VoidAPI.INSTANCE.getBridgeClient().isEnabled()) {
            VoidAPI.INSTANCE.getPunishmentDatabase().proxyBanUnmark(uuid);
        }
    }

    // applies what another server decided. never calls back into the bridge,
    // which is the whole reason bans don't bounce around the network forever

    /**
     * Applies a ban that came over the bridge.
     * True means it's new, so the caller knows whether to bother kicking anyone.
     * Idempotent by ban id: the same ban shows up twice, once live and once in the next SYNC.
     */
    public boolean applyRemoteBan(@NotNull BanPayload ban) {
        // table is keyed by UUID so a name-only ban can't live here.
        // the proxy still refuses the login by name, which is what counts
        if (ban.uuid() == null) return false;

        PunishmentDatabase database = VoidAPI.INSTANCE.getPunishmentDatabase();
        ActiveBanRecord existing = database.activeBanQuery(ban.uuid());
        if (existing != null && existing.banId().equals(ban.banId()) && !existing.isExpired()) {
            database.proxyBanMark(ban.uuid(), ban.banId());
            return false;
        }

        database.activeBanInsert(
                ban.banId(), ban.uuid(), ban.playerName(), ban.kickReason(), ban.expiresAt(), ban.issuedAt());
        database.proxyBanMark(ban.uuid(), ban.banId());
        return true;
    }

    /** Lifts a ban that another server lifted.  Tries UUID first, then name. */
    public void applyRemoteUnban(@Nullable UUID uuid, @NotNull String playerName) {
        PunishmentDatabase database = VoidAPI.INSTANCE.getPunishmentDatabase();
        if (uuid != null && database.activeBanRemove(uuid)) {
            database.proxyBanUnmark(uuid);
            return;
        }
        UUID removed = database.activeBanRemoveByName(playerName);
        if (removed != null) database.proxyBanUnmark(removed);
    }

    /**
     * Drops local bans the proxy lifted while this server was away.
     *
     * Only bans the proxy has vouched for are candidates: a ban that appeared in a
     * previous sync or arrived live from the proxy. A ban issued here that the proxy
     * never acknowledged, such as one made during an outage or in a group that
     * does not share bans, stays enforced.
     *
     * @param live ban id per player UUID, as the proxy currently reports them
     * @return how many stale bans were dropped
     */
    public int reconcileWithProxy(@NotNull Map<UUID, String> live) {
        PunishmentDatabase database = VoidAPI.INSTANCE.getPunishmentDatabase();
        int dropped = 0;
        for (UUID uuid : ProxyBanReconciler.staleBans(database.activeBanAll(), database.proxyBanAll(), live)) {
            if (database.activeBanRemove(uuid)) dropped++;
            database.proxyBanUnmark(uuid);
        }
        return dropped;
    }

    /**
     * Returns the active ban for {@code uuid}, or {@code null} if the player is not banned.
     * Automatically removes expired bans on access.
     */
    public @Nullable ActiveBanRecord getActiveBan(UUID uuid) {
        ActiveBanRecord record = VoidAPI.INSTANCE.getPunishmentDatabase().activeBanQuery(uuid);
        if (record != null && record.isExpired()) {
            VoidAPI.INSTANCE.getPunishmentDatabase().activeBanRemove(uuid);
            return null;
        }
        return record;
    }

    /**
     * Returns the active ban for {@code playerName} (case-insensitive), or {@code null}.
     * Use as a fallback when UUID-based lookup fails (e.g., proxy/offline-mode UUID mismatch).
     */
    public @Nullable ActiveBanRecord getActiveBanByName(String name) {
        ActiveBanRecord record = VoidAPI.INSTANCE.getPunishmentDatabase().activeBanQueryByName(name);
        if (record != null && record.isExpired()) {
            VoidAPI.INSTANCE.getPunishmentDatabase().activeBanRemove(record.uuid());
            return null;
        }
        return record;
    }

    public boolean isBanned(UUID uuid) {
        return getActiveBan(uuid) != null;
    }

    /** Deletes all expired bans from the database.  Safe to call on startup. */
    public void cleanExpired() {
        VoidAPI.INSTANCE.getPunishmentDatabase().activeBanCleanExpired();
    }

    // ── Duration parsing ─────────────────────────────────────────────────

    /**
     * Converts a human-readable duration string to an absolute expiry epoch-ms.
     * Returns 0 for permanent bans.
     *
     * Supported units: s (seconds), m (minutes), h (hours), d (days), w (weeks).
     * Compound values are supported: "1d12h", "2w3d".
     * "permanent", "perm", or "0" all yield a permanent ban.
     */
    public static long parseExpiry(String durationStr) {
        return PunishmentDuration.parseExpiry(durationStr);
    }
}
