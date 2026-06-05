package ac.voidac.manager.tick.impl;

import ac.voidac.VoidAPI;
import ac.voidac.manager.tick.Tickable;
import ac.voidac.player.VoidPlayer;

public class ClearRecentlyUpdatedBlocks implements Tickable {

    private static final int maxTickAge = 2;

    @Override
    public void tick() {
        for (VoidPlayer player : VoidAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.blockHistory.cleanup(VoidAPI.INSTANCE.getTickManager().currentTick - maxTickAge);
        }
    }
}
