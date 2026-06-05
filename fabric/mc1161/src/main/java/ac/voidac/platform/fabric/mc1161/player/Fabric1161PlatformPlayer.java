package ac.voidac.platform.fabric.mc1161.player;

import ac.voidac.platform.api.sender.Sender;
import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.player.AbstractFabricPlatformPlayer;
import ac.voidac.platform.fabric.utils.thread.FabricFutureUtil;
import ac.voidac.utils.math.Location;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class Fabric1161PlatformPlayer extends AbstractFabricPlatformPlayer {
    public Fabric1161PlatformPlayer(ServerPlayer player) {
        super(player);
    }

    @Override
    public Sender getSender() {
        return VoidFabricLoaderPlugin.LOADER.getFabricSenderFactory().wrap(entity.createCommandSourceStack());
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location) {
        return FabricFutureUtil.supplySync(() -> {
            fabricPlayer.teleportTo(
                    (ServerLevel) location.getWorld(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );
            return true;
        });
    }
}
