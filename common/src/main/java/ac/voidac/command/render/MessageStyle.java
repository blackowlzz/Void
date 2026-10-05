package ac.voidac.command.render;

import ac.voidac.utils.anticheat.MessageUtil;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class MessageStyle {
    public final String SEPARATOR = "&8&m                                        ";

    public @NotNull String sc(@NotNull String text) {
        return "<sc>" + text + "</sc>";
    }

    public @NotNull Component info(@NotNull String body) {
        return MessageUtil.miniMessage("%prefix% &7" + body);
    }

    public @NotNull Component success(@NotNull String body) {
        return MessageUtil.miniMessage("%prefix% &a✔ &7" + body);
    }

    public @NotNull Component warning(@NotNull String body) {
        return MessageUtil.miniMessage("%prefix% &e⚠ &7" + body);
    }

    public @NotNull Component error(@NotNull String body) {
        return MessageUtil.miniMessage("%prefix% &c✖ &7" + body);
    }

    public @NotNull Component invalidDuration(@NotNull String value) {
        return error(sc("Invalid duration ") + "&f" + value + "&7. " + sc("Use values such as ")
                + "&f7d&7, &f12h&7, &f1d12h&7 " + sc("or") + " &fpermanent&7.");
    }

    public @NotNull Component header(@NotNull String title, @NotNull String subtitle) {
        return MessageUtil.miniMessage("%prefix% &5" + sc(title) + (subtitle.isEmpty() ? "" : " &8─ &7" + subtitle));
    }

    public @NotNull Component separator() {
        return MessageUtil.miniMessage(SEPARATOR);
    }

    public @NotNull Component bullet(@NotNull String body) {
        return MessageUtil.miniMessage("  &8▸ &7" + body);
    }

    public @NotNull Component detail(@NotNull String label, @NotNull String value) {
        return MessageUtil.miniMessage("  &8▸ &7" + sc(label) + " &8│ &f" + value);
    }
}
