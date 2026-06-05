package ac.voidac.checks.impl.combat;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.player.VoidPlayer;

@CheckData(name = "Hitboxes", stableKey = "void.combat.hitboxes", setback = 10)
public class Hitboxes extends Check {
    public Hitboxes(VoidPlayer player) {
        super(player);
    }
}
