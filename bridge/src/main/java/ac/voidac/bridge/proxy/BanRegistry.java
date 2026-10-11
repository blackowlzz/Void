package ac.voidac.bridge.proxy;

import ac.voidac.bridge.protocol.payload.BanPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class BanRegistry {
    public record Entry(@NotNull String banId,
                        @Nullable UUID uuid,
                        @NotNull String playerName,
                        @NotNull String reason,
                        long expiresAt,
                        long issuedAt,
                        @NotNull String origin,
                        @NotNull Set<String> servers) {
        public boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
        }

        public boolean covers(@NotNull String serverName) {
            return servers.isEmpty() || servers.contains(serverName.toLowerCase(Locale.ROOT));
        }

        public BanPayload toPayload() {
            return new BanPayload(banId, uuid, playerName, reason, expiresAt, issuedAt);
        }
    }

    private static final String BACKUP_NAME = "bans.yml.bak";

    private final Path file;
    private final ProxyPlatform platform;

    private final Map<UUID, List<Entry>> byUuid = new ConcurrentHashMap<>();
    private final Map<String, List<Entry>> byName = new ConcurrentHashMap<>();

    private boolean backedUp;

    public BanRegistry(@NotNull Path dataDirectory, @NotNull ProxyPlatform platform) {
        this.file = dataDirectory.resolve("bans.yml");
        this.platform = platform;
    }

    public @Nullable Entry find(@Nullable UUID uuid, @Nullable String playerName, @Nullable String serverName) {
        List<Entry> candidates = uuid != null ? byUuid.get(uuid) : null;
        if (candidates == null && playerName != null) {
            candidates = byName.get(nameKey(playerName));
        }
        if (candidates == null) return null;

        Entry best = null;
        boolean sawExpired = false;
        for (Entry entry : candidates) {
            if (entry.isExpired()) {
                sawExpired = true;
                continue;
            }
            if (serverName != null && !entry.covers(serverName)) continue;
            if (best == null || outranks(entry, best, serverName == null)) best = entry;
        }
        if (sawExpired) pruneExpired();
        return best;
    }

    public boolean isNetworkWide(@NotNull Entry entry, @NotNull Collection<String> known) {
        if (entry.servers().isEmpty()) return true;
        for (String server : known) {
            if (!entry.covers(server)) return false;
        }
        return true;
    }

    public @NotNull List<BanPayload> activePayloads(@Nullable String forServer) {
        List<BanPayload> out = new ArrayList<>();
        for (List<Entry> entries : byUuid.values()) {
            Entry best = null;
            for (Entry entry : entries) {
                if (entry.isExpired()) continue;
                if (forServer != null && !entry.covers(forServer)) continue;
                if (best == null || outranks(entry, best, false)) best = entry;
            }
            if (best != null) out.add(best.toPayload());
        }
        return out;
    }

    public synchronized boolean put(@NotNull Entry entry) {
        if (entry.isExpired()) return false;

        for (Entry existing : subjectEntries(entry)) {
            if (!existing.isExpired()
                    && existing.banId().equals(entry.banId())
                    && existing.servers().equals(entry.servers())) {
                return false;
            }
        }

        insert(entry);
        save();
        return true;
    }

    public synchronized boolean remove(@Nullable UUID uuid, @Nullable String playerName) {
        List<Entry> doomed = new ArrayList<>();
        List<Entry> byId = uuid != null ? byUuid.get(uuid) : null;
        List<Entry> named = playerName != null ? byName.get(nameKey(playerName)) : null;

        if (byId != null) {
            doomed.addAll(byId);
            if (named != null) {
                for (Entry entry : named) {
                    if (entry.uuid() == null) doomed.add(entry);
                }
            }
        } else if (named != null) {
            doomed.addAll(named);
        }

        if (!delete(doomed)) return false;
        save();
        return true;
    }

    public synchronized void pruneExpired() {
        List<Entry> doomed = new ArrayList<>();
        for (List<Entry> entries : byUuid.values()) {
            for (Entry entry : entries) {
                if (entry.isExpired()) doomed.add(entry);
            }
        }
        for (List<Entry> entries : byName.values()) {
            for (Entry entry : entries) {
                if (entry.isExpired()) doomed.add(entry);
            }
        }
        if (delete(doomed)) save();
    }

    @SuppressWarnings("unchecked")
    public synchronized void load() {
        if (!Files.exists(file)) return;

        List<Map<String, Object>> raw;
        try (InputStream in = Files.newInputStream(file)) {
            Object parsed = new Yaml().load(in);
            if (parsed == null) return;
            if (!(parsed instanceof List)) {
                throw new IllegalStateException("expected a list of bans");
            }
            raw = (List<Map<String, Object>>) parsed;
        } catch (Exception e) {
            platform.warn("bans.yml is unreadable, moving it aside and starting empty", e);
            try {
                Files.move(file, file.resolveSibling("bans.yml.broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
            return;
        }

        int loaded = 0;
        for (Map<String, Object> row : raw) {
            try {
                Object rawUuid = row.get("uuid");
                UUID uuid = rawUuid == null ? null : UUID.fromString(String.valueOf(rawUuid));

                Set<String> servers = new LinkedHashSet<>();
                if (row.get("servers") instanceof List<?> list) {
                    for (Object server : list) {
                        if (server != null) servers.add(String.valueOf(server).toLowerCase(Locale.ROOT));
                    }
                }

                Object rawExpiry = row.get("expires-at");
                long expiresAt = number(rawExpiry);
                if (expiresAt == 0 && rawExpiry != null && !"0".equals(String.valueOf(rawExpiry).trim())) {
                    platform.warn("Ban " + row.get("ban-id") + " in bans.yml has an unreadable expires-at '"
                            + rawExpiry + "', treating it as permanent", null);
                }

                Entry entry = new Entry(
                        String.valueOf(row.get("ban-id")),
                        uuid,
                        String.valueOf(row.get("player-name")),
                        String.valueOf(row.get("reason")),
                        expiresAt,
                        number(row.get("issued-at")),
                        String.valueOf(row.getOrDefault("origin", "unknown")),
                        servers);

                if (entry.isExpired()) continue;
                insert(entry);
                loaded++;
            } catch (Exception malformed) {
                platform.warn("Skipping a malformed row in bans.yml: " + malformed, null);
            }
        }
        platform.info("Loaded " + loaded + " active ban(s).");
    }

    private static boolean outranks(Entry candidate, Entry current, boolean widestFirst) {
        if (widestFirst) {
            boolean candidateWide = candidate.servers().isEmpty();
            if (candidateWide != current.servers().isEmpty()) return candidateWide;
        }
        int byExpiry = Long.compare(lifetime(candidate), lifetime(current));
        if (byExpiry != 0) return byExpiry > 0;
        return candidate.issuedAt() > current.issuedAt();
    }

    private static long lifetime(Entry entry) {
        return entry.expiresAt() == 0 ? Long.MAX_VALUE : entry.expiresAt();
    }

    private static String nameKey(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    private List<Entry> subjectEntries(Entry entry) {
        List<Entry> candidates = entry.uuid() != null
                ? byUuid.get(entry.uuid())
                : byName.get(nameKey(entry.playerName()));
        if (candidates == null) return List.of();
        List<Entry> subject = new ArrayList<>(candidates.size());
        for (Entry existing : candidates) {
            if (sameSubject(existing, entry)) subject.add(existing);
        }
        return subject;
    }

    private static boolean sameSubject(Entry a, Entry b) {
        if (a.uuid() != null) return a.uuid().equals(b.uuid());
        return b.uuid() == null && a.playerName().equalsIgnoreCase(b.playerName());
    }

    private void insert(Entry entry) {
        List<Entry> replaced = new ArrayList<>();
        for (Entry existing : subjectEntries(entry)) {
            if (existing.servers().equals(entry.servers())) replaced.add(existing);
        }
        delete(replaced);

        if (entry.uuid() != null) byUuid.merge(entry.uuid(), List.of(entry), BanRegistry::concat);
        byName.merge(nameKey(entry.playerName()), List.of(entry), BanRegistry::concat);
    }

    private static List<Entry> concat(List<Entry> first, List<Entry> second) {
        List<Entry> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private boolean delete(Collection<Entry> doomed) {
        if (doomed.isEmpty()) return false;

        Set<Entry> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(doomed);
        for (Entry entry : identities) {
            if (entry.uuid() != null) prune(byUuid, entry.uuid(), identities::contains);
            prune(byName, nameKey(entry.playerName()), identities::contains);
        }
        return true;
    }

    private static <K> void prune(Map<K, List<Entry>> index, K key, Predicate<Entry> doomed) {
        index.computeIfPresent(key, (k, entries) -> {
            List<Entry> kept = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                if (!doomed.test(entry)) kept.add(entry);
            }
            return kept.isEmpty() ? null : List.copyOf(kept);
        });
    }

    private synchronized void save() {
        backUpOnce();

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Entry> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<Entry> entries : byUuid.values()) distinct.addAll(entries);
        for (List<Entry> entries : byName.values()) distinct.addAll(entries);

        for (Entry entry : distinct) {
            if (entry.isExpired()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ban-id", entry.banId());
            row.put("uuid", entry.uuid() == null ? null : entry.uuid().toString());
            row.put("player-name", entry.playerName());
            row.put("reason", entry.reason());
            row.put("expires-at", entry.expiresAt());
            row.put("issued-at", entry.issuedAt());
            row.put("origin", entry.origin());
            row.put("servers", new ArrayList<>(entry.servers()));
            rows.add(row);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Path temp = file.resolveSibling("bans.yml.tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                new Yaml(options).dump(rows, out);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            platform.warn("Failed to save bans.yml. In-memory list is still right, "
                    + "it just won't survive a restart", e);
        }
    }

    private void backUpOnce() {
        if (backedUp) return;
        backedUp = true;
        if (!Files.exists(file)) return;
        try {
            Files.copy(file, file.resolveSibling(BACKUP_NAME), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            platform.warn("Could not back up bans.yml before rewriting it", e);
        }
    }

    private static long number(@Nullable Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
