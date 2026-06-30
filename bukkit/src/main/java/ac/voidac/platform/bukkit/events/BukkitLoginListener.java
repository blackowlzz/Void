package ac.voidac.platform.bukkit.events;

import ac.voidac.VoidAPI;
import ac.voidac.manager.punishment.ActiveBanRecord;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

/**
 * Enforces Void's native timed bans at login.
 * Fires on AsyncPlayerPreLoginEvent so the player is denied before entering the server.
 * Only active when VoidBanManager is being used (i.e. LiteBans is not the preferred ban system
 * or LiteBans is not installed).
 */
public class BukkitLoginListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        ActiveBanRecord ban = VoidAPI.INSTANCE.getVoidBanManager().getActiveBan(uuid);
        if (ban == null) {
            // Fallback: covers offline-mode/proxy UUID mismatches where the UUID stored
            // at ban-time differs from the UUID the server sees at login.
            ban = VoidAPI.INSTANCE.getVoidBanManager().getActiveBanByName(event.getName());
        }
        if (ban == null) return;
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, ban.reason());
    }
}
