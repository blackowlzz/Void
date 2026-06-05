package ac.voidac.platform.fabric.initables;

import ac.voidac.VoidAPI;
import ac.voidac.manager.init.start.AbstractTickEndEvent;
import ac.voidac.player.VoidPlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class FabricTickEndEvent extends AbstractTickEndEvent {

    @Override
    public void start() {
        if (!super.shouldInjectEndTick()) {
            return;
        }

        // Register the end-of-tick callback
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
    }

    private void onEndServerTick(MinecraftServer server) {
        tickAllPlayers();
    }

    private void tickAllPlayers() {
        for (VoidPlayer player : VoidAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            if (player.disableVoid) continue;
            super.onEndOfTick(player);
        }
    }
}
