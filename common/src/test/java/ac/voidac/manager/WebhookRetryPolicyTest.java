package ac.voidac.manager;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookRetryPolicyTest {

    private static HttpHeaders headers(String name, String value) {
        return HttpHeaders.of(Map.of(name, List.of(value)), (key, val) -> true);
    }

    private static HttpHeaders noHeaders() {
        return HttpHeaders.of(Map.of(), (key, val) -> true);
    }

    @Test
    void classifiesStatusCodes() {
        assertTrue(WebhookRetryPolicy.isRateLimited(429));
        assertFalse(WebhookRetryPolicy.isRateLimited(503));
        assertTrue(WebhookRetryPolicy.isTransientFailure(500));
        assertTrue(WebhookRetryPolicy.isTransientFailure(503));
        assertFalse(WebhookRetryPolicy.isTransientFailure(404));
        assertFalse(WebhookRetryPolicy.isTransientFailure(429));
        assertFalse(WebhookRetryPolicy.isTransientFailure(204));
    }

    @Test
    void backoffDoublesAndStopsAtTheCap() {
        assertEquals(2_000, WebhookRetryPolicy.backoffMillis(1));
        assertEquals(4_000, WebhookRetryPolicy.backoffMillis(2));
        assertEquals(8_000, WebhookRetryPolicy.backoffMillis(3));
        assertEquals(60_000, WebhookRetryPolicy.backoffMillis(6));
        assertEquals(60_000, WebhookRetryPolicy.backoffMillis(500));
    }

    @Test
    void backoffToleratesNonPositiveAttempts() {
        assertEquals(2_000, WebhookRetryPolicy.backoffMillis(0));
        assertEquals(2_000, WebhookRetryPolicy.backoffMillis(-3));
    }

    @Test
    void rateLimitWithoutHeadersWaitsTheMinimum() {
        assertEquals(1_000, WebhookRetryPolicy.delayMillis(429, noHeaders(), 0));
    }

    @Test
    void transientFailureWithoutHeadersUsesBackoff() {
        assertEquals(4_000, WebhookRetryPolicy.delayMillis(503, noHeaders(), 2));
    }

    @Test
    void retryAfterAcceptsDecimalSeconds() {
        assertEquals(1_500, WebhookRetryPolicy.delayMillis(429, headers("Retry-After", "1.5"), 0));
        assertEquals(7_000, WebhookRetryPolicy.delayMillis(503, headers("Retry-After", "7"), 1));
    }

    @Test
    void resetAfterIsUsedWhenRetryAfterIsMissing() {
        assertEquals(2_250, WebhookRetryPolicy.delayMillis(429, headers("X-RateLimit-Reset-After", "2.25"), 0));
    }

    @Test
    void advertisedDelayIsClamped() {
        assertEquals(60_000, WebhookRetryPolicy.delayMillis(429, headers("Retry-After", "86400"), 0));
        assertEquals(1_000, WebhookRetryPolicy.delayMillis(429, headers("Retry-After", "0"), 0));
        assertEquals(1_000, WebhookRetryPolicy.delayMillis(429, headers("Retry-After", "-5"), 0));
    }

    @Test
    void unparseableHeaderFallsBackToThePolicy() {
        HttpHeaders date = headers("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT");
        assertEquals(1_000, WebhookRetryPolicy.delayMillis(429, date, 0));
        assertEquals(2_000, WebhookRetryPolicy.delayMillis(503, date, 1));
    }
}
