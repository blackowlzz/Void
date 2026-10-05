package ac.voidac.manager;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

public final class ProxyAlertLabel {

    static final String UNLINKED_LABEL = "proxy";

    private ProxyAlertLabel() {
    }

    public static @NotNull String of(boolean bridgeEnabled, @NotNull String serverName) {
        if (!bridgeEnabled || serverName.isBlank()) return UNLINKED_LABEL;
        return MiniMessage.miniMessage().escapeTags(serverName.strip());
    }
}
