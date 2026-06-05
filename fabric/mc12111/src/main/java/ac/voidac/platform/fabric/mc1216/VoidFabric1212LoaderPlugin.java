package ac.voidac.platform.fabric.mc1216;

import ac.voidac.platform.fabric.mc1216.command.Fabric1212PlayerSelectorAdapter;
import ac.voidac.platform.fabric.command.FabricPlayerSelectorParser;
import ac.voidac.platform.fabric.manager.FabricParserDescriptorFactory;
import ac.voidac.platform.fabric.mc1194.VoidFabric1190LoaderPlugin;
import ac.voidac.platform.fabric.mc1194.entity.Fabric1194VoidEntity;
import ac.voidac.platform.fabric.mc1194.player.Fabric1193PlatformInventory;
import ac.voidac.platform.fabric.mc1205.Fabric1203PlatformServer;
import ac.voidac.platform.fabric.mc1205.convert.Fabric1200MessageUtil;
import ac.voidac.platform.fabric.mc1205.convert.Fabric1205ConversionUtil;
import ac.voidac.platform.fabric.mc1216.convert.Fabric1216ConversionUtil;
import ac.voidac.platform.fabric.mc1216.player.Fabric1212PlatformPlayer;
import ac.voidac.platform.fabric.mc1216.player.Fabric1215PlatformInventory;
import ac.voidac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.voidac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;

public class VoidFabric1212LoaderPlugin extends VoidFabric1190LoaderPlugin {

    public VoidFabric1212LoaderPlugin() {
        super(
                LazyHolder.simple(() -> new FabricParserDescriptorFactory(
                        new FabricPlayerSelectorParser<>(Fabric1212PlayerSelectorAdapter::new)
                )),
                new FabricPlatformPlayerFactory(
                        Fabric1212PlatformPlayer::new,
                        Fabric1194VoidEntity::new,
                        PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_21_4)
                            ? Fabric1215PlatformInventory::new : Fabric1193PlatformInventory::new
                ),
                PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_21_10) ?
                        new Fabric12111PlatformServer() : new Fabric1203PlatformServer(),
                new Fabric1200MessageUtil(),
                PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_21_5)
                        ? new Fabric1216ConversionUtil() : new Fabric1205ConversionUtil()
        );
    }

    @Override
    public ServerVersion getNativeVersion() {
        return ServerVersion.V_1_21_11;
    }
}
