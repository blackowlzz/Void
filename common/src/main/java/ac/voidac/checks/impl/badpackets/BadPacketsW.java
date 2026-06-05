package ac.voidac.checks.impl.badpackets;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.player.VoidPlayer;

@CheckData(name = "BadPacketsW", stableKey = "void.badpackets.invalid_entity_target", description = "Interacted with non-existent entity", experimental = true)
public class BadPacketsW extends Check {
    public BadPacketsW(VoidPlayer player) {
        super(player);
    }
}
