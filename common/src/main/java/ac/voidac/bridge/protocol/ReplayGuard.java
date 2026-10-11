package ac.voidac.bridge.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class ReplayGuard {
    private final Map<String, Long> seen = new HashMap<>();

    private long nextSweep = 0L;

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
