package ac.voidac.manager.init.start;

import ac.voidac.VoidAPI;
import ac.voidac.player.VoidPlayer;

public class PacketLimiter implements StartableInitable {
    @Override
    public void start() {
        VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(VoidAPI.INSTANCE.getVoidPlugin(), () -> {
            for (VoidPlayer player : VoidAPI.INSTANCE.getPlayerDataManager().getEntries()) {
                // Avoid concurrent reading on an integer as it's results are unknown
                player.cancelledPackets.set(0);
            }
        }, 1, 20);
    }
}
