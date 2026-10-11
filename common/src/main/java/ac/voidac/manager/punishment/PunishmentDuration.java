package ac.voidac.manager.punishment;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PunishmentDuration {
    private static final Pattern DURATION = Pattern.compile("(?:\\d+[smhdw])+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private PunishmentDuration() {
    }

    public static boolean isValid(String value) {
        try {
            normalize(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Duration is required");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("permanent") || normalized.equals("perm") || normalized.equals("0")) {
            return "permanent";
        }
        if (!DURATION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid duration: " + value);
        }
        parseMilliseconds(normalized);
        return normalized;
    }

    public static long parseExpiry(String value, long now) {
        String normalized = normalize(value);
        long duration = parseMilliseconds(normalized);
        if (duration == 0) {
            return 0;
        }
        try {
            return Math.addExact(now, duration);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Duration is too large: " + value, exception);
        }
    }

    public static long parseExpiry(String value) {
        return parseExpiry(value, System.currentTimeMillis());
    }

    private static long parseMilliseconds(String normalized) {
        if (normalized.equals("permanent")) {
            return 0;
        }

        long total = 0;
        Matcher matcher = PART.matcher(normalized);
        try {
            while (matcher.find()) {
                long value = Long.parseLong(matcher.group(1));
                long multiplier = switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                    case 's' -> 1_000L;
                    case 'm' -> 60_000L;
                    case 'h' -> 3_600_000L;
                    case 'd' -> 86_400_000L;
                    case 'w' -> 604_800_000L;
                    default -> throw new IllegalArgumentException("Unsupported duration unit");
                };
                total = Math.addExact(total, Math.multiplyExact(value, multiplier));
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Duration is too large", exception);
        }
        if (total <= 0) {
            throw new IllegalArgumentException("Duration must be greater than zero");
        }
        return total;
    }
}
