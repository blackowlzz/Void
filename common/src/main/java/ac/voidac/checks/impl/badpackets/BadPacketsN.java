package ac.voidac.checks.impl.badpackets;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.player.VoidPlayer;

@CheckData(name = "BadPacketsN", stableKey = "void.badpackets.invalid_teleport", setback = 0)
public class BadPacketsN extends Check {
    public BadPacketsN(final VoidPlayer player) {
        super(player);
    }
}
