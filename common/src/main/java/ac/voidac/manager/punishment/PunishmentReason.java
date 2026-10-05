package ac.voidac.manager.punishment;

import org.jetbrains.annotations.Nullable;

public final class PunishmentReason {
    private static final String FALLBACK = "Cheating";

    private PunishmentReason() {
    }

    public static String resolve(@Nullable String customReason, @Nullable String configuredDefault) {
        if (customReason != null && !customReason.isBlank()) {
            return customReason.trim();
        }
        if (configuredDefault != null && !configuredDefault.isBlank()) {
            return configuredDefault.trim();
        }
        return FALLBACK;
    }

    public static String applyToTemplate(String template, String reason) {
        if (template.contains("{reason}")) {
            return template.replace("{reason}", reason);
        }
        return template + "\n&7Reason: &f" + reason;
    }
}
