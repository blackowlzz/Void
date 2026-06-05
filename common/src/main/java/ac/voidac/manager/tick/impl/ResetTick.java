package ac.voidac.manager.tick.impl;

import ac.voidac.VoidAPI;
import ac.voidac.manager.tick.Tickable;
import ac.voidac.player.VoidPlayer;

public class ResetTick implements Tickable {
    @Override
    public void tick() {
        for (VoidPlayer player : VoidAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.checkManager.getPacketEntityReplication().tickStartTick();
        }
    }
}
