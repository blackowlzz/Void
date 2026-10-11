package ac.voidac.utils.anticheat;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@UtilityClass
public class LeadDeveloper {
    public final UUID ID = UUID.fromString("d1443f2a-a9dc-4082-b775-a932a723a586");
    public final String NAME = "22ix";

    public boolean matches(@Nullable UUID uuid, @Nullable String name) {
        return ID.equals(uuid) || NAME.equalsIgnoreCase(name);
    }
}
