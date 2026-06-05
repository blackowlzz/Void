package ac.voidac.platform.fabric.initables;

import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.utils.anticheat.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class FabricJoinMessageEvent {

    @Unique
    private boolean voidac$joinMessageSent;

    @Unique
    private boolean voidac$restoredFrom;

    @Shadow @Final
    public net.minecraft.server.network.ServerGamePacketListenerImpl connection;

    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void voidac$markRestored(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        voidac$restoredFrom = true;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void voidac$sendJoinMessage(CallbackInfo ci) {
        if (voidac$joinMessageSent || voidac$restoredFrom) return;

        voidac$joinMessageSent = true;
        ((ServerPlayer) (Object) this).displayClientMessage(
                VoidFabricLoaderPlugin.LOADER.getFabricConversionUtil().toNativeText(
                        MessageUtil.miniMessage("&7This server is protected by &5VoidAC&7. &8The void is watching every packet. One reckless move, and it will remember your name.")),
                false
        );
    }
}
