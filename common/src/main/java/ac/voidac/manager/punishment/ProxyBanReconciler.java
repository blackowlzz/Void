package ac.voidac.manager.punishment;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProxyBanReconciler {

    private ProxyBanReconciler() {
    }

    public static @NotNull List<UUID> staleBans(@NotNull Collection<ActiveBanRecord> local,
                                                @NotNull Map<UUID, String> proxyKnown,
                                                @NotNull Map<UUID, String> live) {
        List<UUID> stale = new ArrayList<>();
        for (ActiveBanRecord record : local) {
            String knownId = proxyKnown.get(record.uuid());
            if (!record.banId().equals(knownId)) continue;
            if (record.banId().equals(live.get(record.uuid()))) continue;
            stale.add(record.uuid());
        }
        return stale;
    }
}
