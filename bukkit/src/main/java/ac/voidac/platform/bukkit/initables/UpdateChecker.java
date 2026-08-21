package ac.voidac.platform.bukkit.initables;

import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import com.google.gson.JsonArray;
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
    private static final String UPDATE_PERMISSION = "void.alerts";
    private static final long TICKS_PER_HOUR = 72000L;

    // rebuilt every cycle, null means nothing to say
    private volatile String joinNotification = null;

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, VoidBukkitLoaderPlugin.LOADER);
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                VoidBukkitLoaderPlugin.LOADER, this::checkForUpdate, 0L, TICKS_PER_HOUR);
    }

    private void checkForUpdate() {
        String current = currentVersion();
        String latest = fetchModrinth();

        // null means the request failed, and a flaky network isn't news
        if (latest == null || !isNewerThan(latest, current)) {
            joinNotification = null;
            return;
        }

        LogUtil.warn("=================================================");
        LogUtil.warn("VoidAC " + latest + " is out. You are running " + current);
        LogUtil.warn("  Download: " + MODRINTH_URL);
        LogUtil.warn("=================================================");

        joinNotification = "&6&lVoidAC &eUpdate Available! &7Running: &c" + current + "<newline>"
                + "  &7Latest: &a" + latest
                + "  &8| &7&n" + MODRINTH_URL;
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

    private static HttpURLConnection openGet(String endpoint) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent",
                "VoidAC/" + currentVersion() + " (update-checker)");
        return conn;
    }

    /** True only if a is strictly newer than b. */
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
            return false;
        } catch (Exception ignored) {
            // unparseable, so fall back to "is it different at all"
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
