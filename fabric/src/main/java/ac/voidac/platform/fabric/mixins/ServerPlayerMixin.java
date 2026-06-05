package ac.voidac.platform.fabric.mixins;

import ac.voidac.VoidAPI;
import ac.voidac.platform.fabric.player.FabricPlatformPlayerFactory;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void onRestoreFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        ((FabricPlatformPlayerFactory) VoidAPI.INSTANCE.getPlatformPlayerFactory()).replaceNativePlayer(oldPlayer.getUUID(), (ServerPlayer) (Object) this);
    }
}
