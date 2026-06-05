package ac.voidac.platform.fabric.mc1205.player;

import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.mc1171.player.Fabric1170PlatformPlayer;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;

public class Fabric1202PlatformPlayer extends Fabric1170PlatformPlayer {
    public Fabric1202PlatformPlayer(ServerPlayer player) {
        super(player);
    }

    @Override
    public void kickPlayer(String textReason) {
        Component reason = LegacyComponentSerializer.legacySection().deserialize(textReason);
        fabricPlayer.connection.disconnect(VoidFabricLoaderPlugin.LOADER.getFabricConversionUtil().toNativeText(reason));
    }
}
