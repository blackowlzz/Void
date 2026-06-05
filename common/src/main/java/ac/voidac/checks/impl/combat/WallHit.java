package ac.voidac.checks.impl.combat;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;

@CheckData(name = "WallHit", configName = "WallHit", stableKey = "void.combat.wallhit", setback = 20)
public class WallHit extends Check implements PacketCheck {
    public WallHit(VoidPlayer player) {
        super(player);
    }
}
