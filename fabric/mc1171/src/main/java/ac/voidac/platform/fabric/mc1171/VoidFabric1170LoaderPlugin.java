package ac.voidac.platform.fabric.mc1171;

import ac.voidac.platform.fabric.AbstractFabricPlatformServer;
import ac.voidac.platform.api.manager.CommandAdapter;
import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.command.FabricPlayerSelectorParser;
import ac.voidac.platform.fabric.manager.FabricParserDescriptorFactory;
import ac.voidac.platform.fabric.mc1171.player.Fabric1170PlatformPlayer;
import ac.voidac.platform.fabric.mc1161.Fabric1140PlatformServer;
import ac.voidac.platform.fabric.mc1161.command.Fabric1161PlayerSelectorAdapter;
import ac.voidac.platform.fabric.mc1161.player.Fabric1161PlatformInventory;
import ac.voidac.platform.fabric.mc1171.entity.Fabric1170VoidEntity;
import ac.voidac.platform.fabric.mc1161.util.convert.Fabric1140ConversionUtil;
import ac.voidac.platform.fabric.mc1161.util.convert.Fabric1161MessageUtil;
import ac.voidac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.voidac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.voidac.platform.fabric.utils.message.IFabricMessageUtil;
import ac.voidac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;


public class VoidFabric1170LoaderPlugin extends VoidFabricLoaderPlugin {

    public VoidFabric1170LoaderPlugin() {
        this(() -> new FabricParserDescriptorFactory(
                        new FabricPlayerSelectorParser<>(Fabric1161PlayerSelectorAdapter::new)
                ),
                new FabricPlatformPlayerFactory(
                        Fabric1170PlatformPlayer::new,
                        Fabric1170VoidEntity::new,
                        Fabric1161PlatformInventory::new
                ),
                PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_17)
                        ? new Fabric1171PlatformServer() : new Fabric1140PlatformServer(),
                new Fabric1161MessageUtil(),
                new Fabric1140ConversionUtil()
        );
    }

    protected VoidFabric1170LoaderPlugin(LazyHolder<CommandAdapter> parserDescriptorFactory,
                                           FabricPlatformPlayerFactory playerFactory,
                                           AbstractFabricPlatformServer platformServer,
                                           IFabricMessageUtil fabricMessageUtil,
                                           IFabricConversionUtil fabricConversionUtil) {
        super(
                parserDescriptorFactory,
                playerFactory,
                platformServer,
                fabricMessageUtil,
                fabricConversionUtil
        );
    }

    @Override
    public ServerVersion getNativeVersion() {
        return ServerVersion.V_1_17_1;
    }
}
