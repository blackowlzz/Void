package ac.voidac.utils.anticheat;

import ac.voidac.VoidAPI;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

import static ac.voidac.command.render.MessageStyle.*;

@UtilityClass
public class LeadDeveloperGreeting {

    public @NotNull List<Component> compose(@NotNull ReleaseStatus status) {
        VoidAPI api = VoidAPI.INSTANCE;
        double tps = api.getPlatformServer().getTPS();
        return List.of(
                info(sc("This server is running Void") + " &5" + api.getExternalAPI().getVoidVersion()),
                bullet(sc("It is ") + describe(status)),
                detail("Platform", api.getPlatformServer().getPlatformImplementationString()),
                detail("Players", api.getPlayerDataManager().getEntries().size() + " &7" + sc("tracked")),
                detail("TPS", Double.isNaN(tps) ? sc("n/a") : String.format(Locale.ROOT, "%.1f", tps)),
                detail("Language", api.getConfigManager().getConfig().getLanguageCode()),
                detail("Ban wave", api.getBanWaveManager().isEnabled()
                        ? "&a" + sc("enabled") + " &8(&f" + api.getBanWaveManager().getQueueSize() + " &7" + sc("queued") + "&8)"
                        : "&c" + sc("disabled")),
                detail("Archive", api.getDataStoreLifecycle().isLoaded()
                        ? "&a" + sc("loaded")
                        : "&c" + sc(api.getDataStoreLifecycle().isEnabled() ? "failed to load" : "disabled"))
        );
    }

    private String describe(ReleaseStatus status) {
        return switch (status.kind()) {
            case UP_TO_DATE -> "&a" + sc("up to date");
            case BEHIND -> "&e" + status.versionsBehind() + " " + sc(status.versionsBehind() == 1 ? "version behind" : "versions behind")
                    + " &8(&7" + sc("latest") + " &f" + status.latest() + "&8)";
            case AHEAD -> "&d" + sc("ahead of the latest release") + " &8(&f" + status.latest() + "&8)";
            case UNKNOWN -> "&7" + sc("unknown, the update check failed");
        };
    }
}
