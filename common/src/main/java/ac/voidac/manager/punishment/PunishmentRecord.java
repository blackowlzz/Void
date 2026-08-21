package ac.voidac.manager.punishment;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PunishmentRecord(
        long id,
        String banId,
        UUID uuid,
        String playerName,
        /** "auto-punish", "banwave", "manual-punish" */
        String type,
        /** staff IGN, or "void-anticheat" for automated actions */
        String issuedBy,
        @Nullable String checkName,
        String reason,
        String duration,
        @Nullable Integer waveId,
        /** e.g. "AntiKB: 14, AntiAura: 7", snapshot at ban time */
        @Nullable String flagsSummary,
        long timestamp
) {}
