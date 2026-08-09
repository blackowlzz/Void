package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Remembers recent nonces so a captured frame can't be resent.
 * Signing gets you authenticity, not freshness: without this, one sniffed BAN
 * frame replays forever and the signature waves it through every single time.
 */
final class ReplayGuard {

    private final Map<String, Long> seen = new HashMap<>();

    // sweeping every frame would go quadratic under load, so do it on a timer
    private long nextSweep = 0L;

    /** False means we've seen this nonce inside the TTL, so bin it. */
    synchronized boolean accept(@NotNull String nonce, long timestamp) {
        long now = System.currentTimeMillis();

        if (now >= nextSweep) {
            sweep(now);
            nextSweep = now + (BridgeProtocol.NONCE_TTL_MS / 2);
        }

        return seen.putIfAbsent(nonce, timestamp) == null;
    }

    private void sweep(long now) {
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > BridgeProtocol.NONCE_TTL_MS) {
                it.remove();
            }
        }
    }
}
