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

    private volatile String latestVersion = null;

    private static final long TICKS_PER_HOUR = 72000L;

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, VoidBukkitLoaderPlugin.LOADER);
        // Initial check + repeat every hour
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                VoidBukkitLoaderPlugin.LOADER, this::checkForUpdate, 0L, TICKS_PER_HOUR);
    }

    private void checkForUpdate() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(MODRINTH_API).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "VoidAC/" + currentVersion() + " (update-checker)");

            if (conn.getResponseCode() != 200) return;

            JsonArray versions = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream())
            ).getAsJsonArray();

            if (versions.isEmpty()) return;

            String latest = versions.get(0).getAsJsonObject().get("version_number").getAsString();
            String current = currentVersion();

            if (latest.equalsIgnoreCase(current)) {
                latestVersion = null;
                return;
            }

            boolean alreadyKnown = latest.equals(latestVersion);
            latestVersion = latest;
            if (!alreadyKnown) {
                LogUtil.warn("=================================================");
                LogUtil.warn("A new VoidAC update is available: " + latest);
                LogUtil.warn("You are running: " + current);
                LogUtil.warn("Download at: " + MODRINTH_URL);
                LogUtil.warn("=================================================");
            }
        } catch (Exception ignored) {
            // Network issues — silently skip update check
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (latestVersion == null) return;
        var player = event.getPlayer();
        if (!player.hasPermission(UPDATE_PERMISSION)) return;

        Bukkit.getScheduler().runTaskLater(VoidBukkitLoaderPlugin.LOADER, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(MessageUtil.miniMessage(
                    "&6&lVoidAC &eUpdate Available! &7Latest: &a" + latestVersion
                            + " &7| Running: &c" + currentVersion()
                            + " &7| &nhttps://modrinth.com/plugin/voidac"
            ));
        }, 60L);
    }

    private static String currentVersion() {
        return VoidBukkitLoaderPlugin.LOADER.getDescription().getVersion();
    }
}
