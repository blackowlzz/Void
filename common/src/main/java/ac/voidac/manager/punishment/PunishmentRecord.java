package ac.voidac.manager.punishment;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PunishmentRecord(
        long id,
        String banId,
        UUID uuid,
        String playerName,
        String type,
        String issuedBy,
        @Nullable String checkName,
        String reason,
        String duration,
        @Nullable Integer waveId,
        @Nullable String flagsSummary,
        long timestamp
) {}
