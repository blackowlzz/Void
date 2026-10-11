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

public class VoidBanManager {
    public void ban(String banId, UUID uuid, String playerName, String kickReason, String durationStr, long timestamp) {
        long expiresAt = PunishmentDuration.parseExpiry(durationStr);
        database().activeBanInsert(banId, uuid, playerName, kickReason, expiresAt, timestamp);
        VoidAPI.INSTANCE.getBridgeClient().sendBan(banId, uuid, playerName, kickReason, expiresAt, timestamp);
    }

    public @Nullable UUID unbanByName(String name) {
        ActiveBanRecord existing = database().activeBanQueryByName(name);
        UUID removed = database().activeBanRemoveByName(name);
        if (removed != null) {
            if (VoidAPI.INSTANCE.getBridgeClient().isEnabled()) {
                database().proxyBanUnmark(removed);
            }
            VoidAPI.INSTANCE.getBridgeClient().sendUnban(removed, name, existing != null ? existing.banId() : "");
        }
        return removed;
    }

    public boolean applyRemoteBan(@NotNull BanPayload ban) {
        if (ban.uuid() == null) return false;

        PunishmentDatabase database = database();
        ActiveBanRecord existing = database.activeBanQuery(ban.uuid());
        boolean alreadyApplied = existing != null && existing.banId().equals(ban.banId()) && !existing.isExpired();
        if (!alreadyApplied) {
            database.activeBanInsert(ban.banId(), ban.uuid(), ban.playerName(), ban.kickReason(), ban.expiresAt(), ban.issuedAt());
        }
        database.proxyBanMark(ban.uuid(), ban.banId());
        return !alreadyApplied;
    }

    public void applyRemoteUnban(@Nullable UUID uuid, @NotNull String playerName) {
        PunishmentDatabase database = database();
        if (uuid != null && database.activeBanRemove(uuid)) {
            database.proxyBanUnmark(uuid);
            return;
        }
        UUID removed = database.activeBanRemoveByName(playerName);
        if (removed != null) database.proxyBanUnmark(removed);
    }

    public int reconcileWithProxy(@NotNull Map<UUID, String> live) {
        PunishmentDatabase database = database();
        int dropped = 0;
        for (UUID uuid : ProxyBanReconciler.staleBans(database.activeBanAll(), database.proxyBanAll(), live)) {
            if (database.activeBanRemove(uuid)) dropped++;
            database.proxyBanUnmark(uuid);
        }
        return dropped;
    }

    public @Nullable ActiveBanRecord getActiveBan(UUID uuid) {
        return dropIfExpired(database().activeBanQuery(uuid));
    }

    public @Nullable ActiveBanRecord getActiveBanByName(String name) {
        return dropIfExpired(database().activeBanQueryByName(name));
    }

    public void cleanExpired() {
        database().activeBanCleanExpired();
    }

    private static @Nullable ActiveBanRecord dropIfExpired(@Nullable ActiveBanRecord record) {
        if (record != null && record.isExpired()) {
            database().activeBanRemove(record.uuid());
            return null;
        }
        return record;
    }

    private static PunishmentDatabase database() {
        return VoidAPI.INSTANCE.getPunishmentDatabase();
    }
}
