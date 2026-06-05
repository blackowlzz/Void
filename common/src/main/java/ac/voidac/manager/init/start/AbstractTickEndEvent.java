package ac.voidac.manager.init.start;

import ac.voidac.VoidAPI;
import ac.voidac.player.VoidPlayer;

// Intended for future events we inject all platforms at the end of a tick
public abstract class AbstractTickEndEvent implements StartableInitable {

    @Override
    public void start() {

    }

    protected void onEndOfTick(VoidPlayer player) {
        player.checkManager.getPacketEntityReplication().onEndOfTickEvent();
    }

    protected boolean shouldInjectEndTick() {
        return VoidAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("Reach.enable-post-packet", false);
    }
}
