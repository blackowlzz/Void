package ac.voidac.bridge.proxy;

import ac.voidac.bridge.protocol.payload.BanPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The network's ban list. Lives on the proxy because the proxy is what actually
 * refuses the login, so a backend being down stops being anybody's problem.
 *
 * Reads sit on the login path so they're lock-free. Writes are rare.
 */
public final class BanRegistry {

    /**
     * One ban plus where it applies.
     * Empty servers set means the whole network.
     */
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

    private final Path file;
    private final ProxyPlatform platform;

    private final Map<UUID, Entry> byUuid = new ConcurrentHashMap<>();
    // fallback for when offline-mode UUIDs disagree across the network
    private final Map<String, Entry> byName = new ConcurrentHashMap<>();

    public BanRegistry(@NotNull Path dataDirectory, @NotNull ProxyPlatform platform) {
        this.file = dataDirectory.resolve("bans.yml");
        this.platform = platform;
    }

    /** Pass a null serverName to ask "is this player banned from anything at all". */
    public @Nullable Entry find(@Nullable UUID uuid, @Nullable String playerName, @Nullable String serverName) {
        Entry entry = uuid != null ? byUuid.get(uuid) : null;
        if (entry == null && playerName != null) {
            entry = byName.get(playerName.toLowerCase(Locale.ROOT));
        }
        if (entry == null) return null;

        if (entry.isExpired()) {
            remove(entry.uuid(), entry.playerName());
            return null;
        }
        if (serverName != null && !entry.covers(serverName)) return null;
        return entry;
    }

    public boolean isNetworkWide(@NotNull Entry entry, @NotNull Collection<String> known) {
        if (entry.servers().isEmpty()) return true;
        for (String server : known) {
            if (!entry.covers(server)) return false;
        }
        return true;
    }

    /** Every live ban, for answering a SYNC. */
    public @NotNull List<BanPayload> activePayloads(@Nullable String forServer) {
        List<BanPayload> out = new ArrayList<>();
        for (Entry entry : byUuid.values()) {
            if (entry.isExpired()) continue;
            if (forServer != null && !entry.covers(forServer)) continue;
            out.add(entry.toPayload());
        }
        // name-only bans have no UUID and backends can't store them anyway,
        // so they stay proxy-side and simply don't get synced
        return out;
    }

    /**
     * True means something changed. A repeat of the same ban id returns false,
     * which is what stops the second arrival (live, then SYNC) causing another
     * round of kicks.
     */
    public boolean put(@NotNull Entry entry) {
        if (entry.isExpired()) return false;

        Entry existing = entry.uuid() != null
                ? byUuid.get(entry.uuid())
                : byName.get(entry.playerName().toLowerCase(Locale.ROOT));
        if (existing != null && existing.banId().equals(entry.banId()) && !existing.isExpired()) {
            return false;
        }

        if (entry.uuid() != null) byUuid.put(entry.uuid(), entry);
        byName.put(entry.playerName().toLowerCase(Locale.ROOT), entry);
        save();
        return true;
    }

    public boolean remove(@Nullable UUID uuid, @Nullable String playerName) {
        boolean changed = false;
        if (uuid != null) {
            Entry removed = byUuid.remove(uuid);
            if (removed != null) {
                byName.remove(removed.playerName().toLowerCase(Locale.ROOT));
                changed = true;
            }
        }
        if (playerName != null) {
            Entry removed = byName.remove(playerName.toLowerCase(Locale.ROOT));
            if (removed != null) {
                if (removed.uuid() != null) byUuid.remove(removed.uuid());
                changed = true;
            }
        }
        if (changed) save();
        return changed;
    }

    public void pruneExpired() {
        boolean changed = byUuid.values().removeIf(Entry::isExpired);
        changed |= byName.values().removeIf(Entry::isExpired);
        if (changed) save();
    }

    /** A corrupt file gets renamed, not deleted. Losing a ban list quietly is how cheaters walk back in. */
    @SuppressWarnings("unchecked")
    public void load() {
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
                // nothing useful left to do, the warning above is the point
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

                Entry entry = new Entry(
                        String.valueOf(row.get("ban-id")),
                        uuid,
                        String.valueOf(row.get("player-name")),
                        String.valueOf(row.get("reason")),
                        number(row.get("expires-at")),
                        number(row.get("issued-at")),
                        String.valueOf(row.getOrDefault("origin", "unknown")),
                        servers);

                if (entry.isExpired()) continue;
                if (uuid != null) byUuid.put(uuid, entry);
                byName.put(entry.playerName().toLowerCase(Locale.ROOT), entry);
                loaded++;
            } catch (Exception malformed) {
                platform.warn("Skipping a malformed row in bans.yml: " + malformed, null);
            }
        }
        platform.info("Loaded " + loaded + " active ban(s).");
    }

    /** Temp file plus a move, so a crash mid-write can't leave a half a ban list. */
    private synchronized void save() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> written = new LinkedHashSet<>();

        for (Entry entry : byName.values()) {
            if (entry.isExpired() || !written.add(entry.banId())) continue;
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
