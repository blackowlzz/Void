package ac.voidac.utils.anticheat;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@UtilityClass
public class LeadDeveloper {
    public final UUID ID = UUID.fromString("d1443f2a-a9dc-4082-b775-a932a723a586");
    public final String TITLE = "VoidAC Dev";
    public final String JOIN_FORMAT = "%prefix% &5%player% &7(" + TITLE + ") <sc>joined the server</sc> &8(&7%brand%&8)";

    public boolean matches(@NotNull UUID uuid, boolean onlineMode) {
        return onlineMode && ID.equals(uuid);
    }
}
