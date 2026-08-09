package ac.voidac.manager;

import ac.voidac.VoidAPI;
import ac.voidac.bridge.protocol.payload.BanPayload;
import ac.voidac.manager.punishment.ActiveBanRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native timed-ban enforcement for Void.
 * Used as a fallback when LiteBans (or another ban plugin) is not installed.
 *
 * Bans are stored in the {@code void_active_bans} table of {@code punishments.db}.
 * Enforcement happens at login via {@code BukkitLoginListener} (AsyncPlayerPreLoginEvent).
 */
public class VoidBanManager {

    private static final Pattern UNIT_PATTERN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

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
            VoidAPI.INSTANCE.getBridgeClient().sendUnban(removed, name, existing != null ? existing.banId() : "");
        }
        return removed;
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

        ActiveBanRecord existing = VoidAPI.INSTANCE.getPunishmentDatabase().activeBanQuery(ban.uuid());
        if (existing != null && existing.banId().equals(ban.banId()) && !existing.isExpired()) {
            return false;
        }

        VoidAPI.INSTANCE.getPunishmentDatabase().activeBanInsert(
                ban.banId(), ban.uuid(), ban.playerName(), ban.kickReason(), ban.expiresAt(), ban.issuedAt());
        return true;
    }

    /** Lifts a ban that another server lifted.  Tries UUID first, then name. */
    public void applyRemoteUnban(@Nullable UUID uuid, @NotNull String playerName) {
        if (uuid != null && VoidAPI.INSTANCE.getPunishmentDatabase().activeBanRemove(uuid)) return;
        VoidAPI.INSTANCE.getPunishmentDatabase().activeBanRemoveByName(playerName);
    }

    /**
     * Drops local bans the proxy no longer knows about.
     *
     * Without this a backend can stay stricter than the network: unban someone
     * while this server happens to be empty and the UNBAN has nobody to travel
     * on, so our copy sticks around and we keep refusing a player the proxy is
     * perfectly happy with. The proxy's list is the truth, so a sync is a
     * reconciliation and not just a merge.
     *
     * @param liveBanIds ban ids the proxy says still apply here
     * @return how many stale bans were dropped
     */
    public int reconcileWithProxy(@NotNull Set<String> liveBanIds) {
        int dropped = 0;
        for (ActiveBanRecord record : VoidAPI.INSTANCE.getPunishmentDatabase().activeBanAll()) {
            if (liveBanIds.contains(record.banId())) continue;
            if (VoidAPI.INSTANCE.getPunishmentDatabase().activeBanRemove(record.uuid())) dropped++;
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
        if (durationStr == null || durationStr.isBlank()) return 0;
        String s = durationStr.trim().toLowerCase(Locale.ROOT);
        if (s.equals("permanent") || s.equals("perm") || s.equals("0")) return 0;

        long totalMs = 0;
        Matcher m = UNIT_PATTERN.matcher(s);
        while (m.find()) {
            long value = Long.parseLong(m.group(1));
            char unit  = Character.toLowerCase(m.group(2).charAt(0));
            totalMs += switch (unit) {
                case 's' -> value * 1_000L;
                case 'm' -> value * 60_000L;
                case 'h' -> value * 3_600_000L;
                case 'd' -> value * 86_400_000L;
                case 'w' -> value * 604_800_000L;
                default  -> 0L;
            };
        }
        return totalMs > 0 ? System.currentTimeMillis() + totalMs : 0;
    }
}
