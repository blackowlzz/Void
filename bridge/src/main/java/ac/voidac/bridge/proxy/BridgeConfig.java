package ac.voidac.bridge.proxy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The proxy's config.yml.
 * Forgiving about shape on purpose, a typo should get you a warning and a
 * default, not a proxy that refuses to boot. The secret is the one exception,
 * because running unauthenticated is worse than not running.
 */
public final class BridgeConfig {

    /** Backends that share alerts and bans with each other. */
    public record Group(@NotNull String name,
                        @NotNull Set<String> servers,
                        boolean shareAlerts,
                        boolean shareBans) {
    }

    private final String secret;
    private final int port;
    private final String bindHost;
    private final List<Group> groups;
    private final boolean linkUnlistedServers;
    private final boolean enforceAtLogin;
    private final String banScreenPrefix;

    private BridgeConfig(String secret, int port, String bindHost, List<Group> groups,
                         boolean linkUnlistedServers, boolean enforceAtLogin, String banScreenPrefix) {
        this.secret = secret;
        this.port = port;
        this.bindHost = bindHost;
        this.groups = groups;
        this.linkUnlistedServers = linkUnlistedServers;
        this.enforceAtLogin = enforceAtLogin;
        this.banScreenPrefix = banScreenPrefix;
    }

    public @NotNull String secret() {
        return secret;
    }

    public int port() {
        return port;
    }

    public @NotNull String bindHost() {
        return bindHost;
    }

    public boolean enforceAtLogin() {
        return enforceAtLogin;
    }

    public @NotNull String banScreenPrefix() {
        return banScreenPrefix;
    }

    /** Whether this server shares this kind of thing at all. A group can share alerts but not bans. */
    public boolean shares(@NotNull String origin, boolean forBans) {
        Group group = groupOf(origin);
        if (group != null) return forBans ? group.shareBans() : group.shareAlerts();
        // ungrouped: either the whole network is one implicit group, or the
        // operator listed groups carefully and meant to leave this one out
        return linkUnlistedServers;
    }

    /**
     * How far a message from this server reaches.
     * Empty set means the whole network, and that distinction matters: stored
     * empty, a network-wide ban keeps covering backends you add later, whereas
     * a snapshot of today's server list would not.
     */
    public @NotNull Set<String> scopeOf(@NotNull String origin, boolean forBans) {
        Group group = groupOf(origin);
        if (group == null) return Set.of();
        if (forBans ? !group.shareBans() : !group.shareAlerts()) return Set.of();
        return group.servers();
    }

    private @Nullable Group groupOf(@NotNull String server) {
        for (Group group : groups) {
            if (containsIgnoreCase(group.servers(), server)) return group;
        }
        return null;
    }

    private static boolean containsIgnoreCase(Set<String> set, String value) {
        for (String entry : set) {
            if (entry.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    /** Null means the secret is still unset, so the bridge must stay off. */
    @SuppressWarnings("unchecked")
    public static @Nullable BridgeConfig load(@NotNull Path dataDirectory, @NotNull ProxyPlatform platform) {
        Path file = dataDirectory.resolve("config.yml");
        try {
            Files.createDirectories(dataDirectory);
            if (!Files.exists(file)) {
                String generated = copyDefault(file);
                platform.info("First run, so a config.yml with a fresh secret was generated.");
                platform.info("Put this in bridge.yml on every backend you want linked:");
                platform.info("    secret: \"" + generated + "\"");
            }
        } catch (Exception e) {
            platform.warn("Could not create the bridge config directory", e);
            return null;
        }

        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file)) {
            Object parsed = new Yaml().load(in);
            root = parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (Exception e) {
            platform.warn("config.yml is not valid YAML, bridge stays off until you fix it", e);
            return null;
        }

        String secret = string(root.get("secret"), "");
        if (secret.isBlank() || secret.equals("CHANGE-ME")) {
            platform.warn("'secret' is unset in config.yml, so the bridge stays off. "
                    + "Delete config.yml and restart to get a fresh generated one.", null);
            return null;
        }
        if (secret.length() < 16) {
            platform.warn("'secret' is short. It is the only thing between a stranger on your port "
                    + "and a network-wide ban, so use a long random one.", null);
        }

        int port = (int) number(root.get("port"), 25599);
        String bindHost = string(root.get("bind"), "0.0.0.0");
        List<Group> groups = parseGroups(root.get("groups"), platform);
        boolean linkUnlisted = bool(root.get("link-unlisted-servers"), true);
        boolean enforceAtLogin = bool(root.get("enforce-at-login"), true);
        String prefix = string(root.get("ban-screen-prefix"), "");

        if (groups.isEmpty()) {
            platform.info("No groups configured, so every backend shares with every other.");
        }
        return new BridgeConfig(secret, port, bindHost, groups, linkUnlisted, enforceAtLogin, prefix);
    }

    @SuppressWarnings("unchecked")
    private static List<Group> parseGroups(@Nullable Object raw, @NotNull ProxyPlatform platform) {
        List<Group> groups = new ArrayList<>();
        if (!(raw instanceof Map)) return groups;

        for (Map.Entry<String, Object> entry : ((Map<String, Object>) raw).entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                platform.warn("Group '" + name + "' is not a section, ignoring it.", null);
                continue;
            }
            Map<String, Object> body = (Map<String, Object>) entry.getValue();

            Set<String> servers = new LinkedHashSet<>();
            if (body.get("servers") instanceof List<?> list) {
                for (Object server : list) {
                    if (server != null) servers.add(String.valueOf(server));
                }
            }
            if (servers.isEmpty()) {
                platform.warn("Group '" + name + "' lists no servers, ignoring it.", null);
                continue;
            }

            groups.add(new Group(name, servers,
                    bool(body.get("share-alerts"), true),
                    bool(body.get("share-bans"), true)));
        }
        return groups;
    }

    /**
     * Writes the bundled default with a real secret already filled in.
     * Nobody should have to go hunting for openssl to get a working plugin, and
     * a generated 64 hex chars beats whatever an admin would have typed.
     */
    private static String copyDefault(Path target) throws Exception {
        String generated = newSecret();
        try (InputStream in = BridgeConfig.class.getResourceAsStream("/config.yml")) {
            if (in == null) {
                Files.writeString(target, "secret: \"" + generated + "\"\n", StandardCharsets.UTF_8);
                return generated;
            }
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Files.writeString(target, template.replace("CHANGE-ME", generated), StandardCharsets.UTF_8);
        }
        return generated;
    }

    private static String newSecret() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        StringBuilder hex = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static long number(@Nullable Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String string(@Nullable Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean bool(@Nullable Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value == null) return fallback;
        String text = String.valueOf(value).toLowerCase(Locale.ROOT);
        return text.equals("true") || text.equals("yes") || text.equals("on");
    }
}
