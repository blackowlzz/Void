package ac.voidac.platform.fabric.manager;

import ac.voidac.platform.api.manager.ItemResetHandler;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.platform.fabric.utils.convert.FabricConversionUtil;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class FabricItemResetHandler implements ItemResetHandler {
    @Override
    public void resetItemUsage(@Nullable PlatformPlayer player) {
        if (player != null) {
            ((ServerPlayer) player.getNative()).stopUsingItem();
        }
    }

    @Override
    public @Nullable InteractionHand getItemUsageHand(@Nullable PlatformPlayer platformPlayer) {
        if (platformPlayer == null) {
            return null;
        }

        ServerPlayer player = (ServerPlayer) platformPlayer.getNative();
        return player.isUsingItem() ? FabricConversionUtil.fromFabricHand(player.getUsedItemHand()) : null;
    }

    @Override
    public boolean isUsingItem(@Nullable PlatformPlayer player) {
        return player != null && ((ServerPlayer) player.getNative()).isUsingItem();
    }
}
