package ac.voidac.manager.punishment;

import java.util.UUID;

public record ActiveBanRecord(
        String banId,
        UUID uuid,
        String playerName,
        String reason,
        long expiresAt,   // 0 = permanent; otherwise epoch-ms when the ban expires
        long timestamp
) {
    public boolean isPermanent() { return expiresAt == 0; }
    public boolean isExpired()   { return expiresAt != 0 && System.currentTimeMillis() > expiresAt; }

    public String remainingTime() {
        if (isPermanent()) return "permanent";
        long ms = expiresAt - System.currentTimeMillis();
        if (ms <= 0) return "expired";
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        if (m > 0) return m + "m";
        return s + "s";
    }
}
