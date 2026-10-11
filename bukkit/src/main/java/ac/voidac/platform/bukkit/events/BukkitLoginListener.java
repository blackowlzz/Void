package ac.voidac.platform.bukkit.events;

import ac.voidac.VoidAPI;
import ac.voidac.manager.punishment.ActiveBanRecord;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

public class BukkitLoginListener implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        ActiveBanRecord ban = VoidAPI.INSTANCE.getVoidBanManager().getActiveBan(uuid);
        if (ban == null) {
            ban = VoidAPI.INSTANCE.getVoidBanManager().getActiveBanByName(event.getName());
        }
        if (ban == null) return;
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, ban.reason());
    }
}
