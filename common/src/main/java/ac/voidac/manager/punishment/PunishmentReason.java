package ac.voidac.manager.punishment;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class PunishmentReason {
    private static final String FALLBACK = "Cheating";
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+\\s*");
    private static final Pattern REPEATED_SPACES = Pattern.compile("\\s{2,}");

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

    public static String toSingleLine(String multiLine, String separator) {
        String joined = LINE_BREAKS.matcher(multiLine).replaceAll(separator);
        return REPEATED_SPACES.matcher(joined).replaceAll(" ").trim();
    }
}
