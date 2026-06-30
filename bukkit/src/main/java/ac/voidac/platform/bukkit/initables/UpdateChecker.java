package ac.voidac.platform.bukkit.initables;

import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker implements StartableInitable, Listener {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/voidac/version";
    private static final String MODRINTH_URL = "https://modrinth.com/plugin/voidac";
    private static final String BDEVS_API    = "https://bdevs.it/api/plugins/voidac/version";
    private static final String BDEVS_URL    = "https://bdevs.it/resources/voidac";
    private static final String UPDATE_PERMISSION = "void.alerts";
    private static final long TICKS_PER_HOUR = 72000L;

    // Rebuilt each check cycle; null = nothing to tell
    private volatile String joinNotification = null;

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, VoidBukkitLoaderPlugin.LOADER);
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                VoidBukkitLoaderPlugin.LOADER, this::checkForUpdate, 0L, TICKS_PER_HOUR);
    }

    private void checkForUpdate() {
        String current  = currentVersion();
        String modrinth = fetchModrinth();
        String bdevs    = fetchBdevs();

        // Only flag if a marketplace has a version strictly newer than what we're running.
        boolean outdatedOnModrinth = modrinth != null && isNewerThan(modrinth, current);
        boolean outdatedOnBdevs    = bdevs    != null && isNewerThan(bdevs, current);
        // True when both fetched but report different versions (regardless of direction).
        boolean platformsMismatch  = modrinth != null && bdevs != null
                && !modrinth.equalsIgnoreCase(bdevs);

        if (!outdatedOnModrinth && !outdatedOnBdevs) {
            joinNotification = null;
            return;
        }

        // Console notice
        LogUtil.warn("=================================================");
        if (outdatedOnModrinth && outdatedOnBdevs) {
            if (platformsMismatch) {
                LogUtil.warn("VoidAC update available, versions differ between marketplaces!");
                LogUtil.warn("  Modrinth: " + modrinth + "  ->  " + MODRINTH_URL);
                LogUtil.warn("  bdevs.it: " + bdevs    + "  ->  " + BDEVS_URL);
                LogUtil.warn("  Running : " + current);
                LogUtil.warn("  Download from whichever marketplace has the higher version.");
            } else {
                LogUtil.warn("VoidAC " + modrinth + " is available! You are running " + current);
                LogUtil.warn("  Modrinth: " + MODRINTH_URL);
                LogUtil.warn("  bdevs.it: " + BDEVS_URL);
            }
        } else if (outdatedOnModrinth) {
            LogUtil.warn("VoidAC " + modrinth + " is available on Modrinth! You are running " + current);
            LogUtil.warn("  Download: " + MODRINTH_URL);
            if (platformsMismatch) {
                LogUtil.warn("  Note: bdevs.it does not yet carry this release (shows " + bdevs + ").");
            }
        } else {
            LogUtil.warn("VoidAC " + bdevs + " is available on bdevs.it! You are running " + current);
            LogUtil.warn("  Download: " + BDEVS_URL);
            if (platformsMismatch) {
                LogUtil.warn("  Note: Modrinth does not yet carry this release (shows " + modrinth + ").");
            }
        }
        LogUtil.warn("=================================================");

        joinNotification = buildJoinMessage(
                current, modrinth, bdevs,
                outdatedOnModrinth, outdatedOnBdevs, platformsMismatch);
    }

    private static String buildJoinMessage(String current,
                                           String modrinth, String bdevs,
                                           boolean outdatedOnModrinth, boolean outdatedOnBdevs,
                                           boolean platformsMismatch) {
        StringBuilder sb = new StringBuilder();
        sb.append("&6&lVoidAC &eUpdate Available! &7Running: &c").append(current).append("<newline>");

        if (outdatedOnModrinth && outdatedOnBdevs) {
            if (platformsMismatch) {
                sb.append("  &7Modrinth &8: &a").append(modrinth)
                  .append("  &7bdevs.it &8: &a").append(bdevs)
                  .append("<newline>")
                  .append("  &eVersions differ between platforms. Download the highest one.");
            } else {
                sb.append("  &7Latest: &a").append(modrinth).append("<newline>")
                  .append("  &7Modrinth &8: &nhttps://modrinth.com/plugin/voidac")
                  .append("  &7| bdevs.it &8: &nhttps://bdevs.it/plugins/voidac");
            }
        } else if (outdatedOnModrinth) {
            sb.append("  &7Modrinth &8: &a").append(modrinth)
              .append("  &7&nhttps://modrinth.com/plugin/voidac");
            if (platformsMismatch) {
                sb.append("<newline>  &7bdevs.it does not yet carry this release &8(shows &e")
                  .append(bdevs).append("&8)");
            }
        } else {
            sb.append("  &7bdevs.it &8: &a").append(bdevs)
              .append("  &7&nhttps://bdevs.it/plugins/voidac");
            if (platformsMismatch) {
                sb.append("<newline>  &7Modrinth does not yet carry this release &8(shows &e")
                  .append(modrinth).append("&8)");
            }
        }

        return sb.toString();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        String notification = joinNotification;
        if (notification == null) return;
        var player = event.getPlayer();
        if (!player.hasPermission(UPDATE_PERMISSION)) return;

        Bukkit.getScheduler().runTaskLater(VoidBukkitLoaderPlugin.LOADER, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(MessageUtil.miniMessage(notification));
        }, 60L);
    }

    // ── Fetch helpers ─────────────────────────────────────────────────────────────

    private String fetchModrinth() {
        try {
            HttpURLConnection conn = openGet(MODRINTH_API);
            if (conn.getResponseCode() != 200) return null;

            JsonArray versions = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream())).getAsJsonArray();
            if (versions.isEmpty()) return null;
            return versions.get(0).getAsJsonObject().get("version_number").getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchBdevs() {
        try {
            HttpURLConnection conn = openGet(BDEVS_API);
            if (conn.getResponseCode() != 200) return null;

            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            return json.get("version").getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HttpURLConnection openGet(String endpoint) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent",
                "VoidAC/" + currentVersion() + " (update-checker)");
        return conn;
    }

    // ── Version comparison ────────────────────────────────────────────────────────

    // Returns true only if a is strictly newer than b (semver dot comparison).
    private static boolean isNewerThan(String a, String b) {
        try {
            int[] av = parseSemver(a);
            int[] bv = parseSemver(b);
            int len = Math.max(av.length, bv.length);
            for (int i = 0; i < len; i++) {
                int ai = i < av.length ? av[i] : 0;
                int bi = i < bv.length ? bv[i] : 0;
                if (ai != bi) return ai > bi;
            }
            return false; // equal
        } catch (Exception ignored) {
            // Unparseable version string: fall back to inequality check
            return !a.equalsIgnoreCase(b);
        }
    }

    private static int[] parseSemver(String version) {
        String[] parts = version.replaceAll("[^0-9.]", "").split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
        }
        return nums;
    }

    private static String currentVersion() {
        return VoidBukkitLoaderPlugin.LOADER.getDescription().getVersion();
    }
}
