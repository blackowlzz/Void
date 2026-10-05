package ac.voidac.manager;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class WebhookRetryPolicy {
    static final int MAX_ATTEMPTS = 5;
    private static final long BASE_DELAY_MS = 2_000;
    private static final long MIN_DELAY_MS = 1_000;
    private static final long MAX_DELAY_MS = 60_000;
    private static final int MAX_BACKOFF_SHIFT = 5;
    private static final List<String> DELAY_HEADERS = List.of("Retry-After", "X-RateLimit-Reset-After");

    private WebhookRetryPolicy() {
    }

    static boolean isRateLimited(int status) {
        return status == 429;
    }

    static boolean isTransientFailure(int status) {
        return status >= 500;
    }

    static long delayMillis(int status, HttpHeaders headers, int attempt) {
        OptionalLong advertised = advertisedDelayMillis(headers);
        if (advertised.isPresent()) return clamp(advertised.getAsLong());
        return isRateLimited(status) ? MIN_DELAY_MS : backoffMillis(attempt);
    }

    static long backoffMillis(int attempt) {
        int shift = Math.min(Math.max(attempt - 1, 0), MAX_BACKOFF_SHIFT);
        return clamp(BASE_DELAY_MS << shift);
    }

    private static OptionalLong advertisedDelayMillis(HttpHeaders headers) {
        for (String name : DELAY_HEADERS) {
            Optional<String> value = headers.firstValue(name);
            if (value.isEmpty()) continue;
            try {
                return OptionalLong.of((long) (Double.parseDouble(value.get().trim()) * 1000));
            } catch (NumberFormatException ignored) {
            }
        }
        return OptionalLong.empty();
    }

    private static long clamp(long millis) {
        return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, millis));
    }
}
