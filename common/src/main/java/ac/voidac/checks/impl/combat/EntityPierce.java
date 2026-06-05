package ac.voidac.checks.impl.combat;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;

@CheckData(name = "EntityPierce", configName = "EntityPierce", stableKey = "void.combat.entitypierce", setback = 30)
public class EntityPierce extends Check implements PacketCheck {
    public EntityPierce(VoidPlayer player) {
        super(player);
    }
}
