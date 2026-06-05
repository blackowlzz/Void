package ac.voidac.platform.fabric.mc1161;

import ac.voidac.platform.fabric.AbstractFabricPlatformServer;
import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.command.FabricPlayerSelectorParser;
import ac.voidac.platform.fabric.manager.FabricParserDescriptorFactory;
import ac.voidac.platform.fabric.mc1161.command.Fabric1161PlayerSelectorAdapter;
import ac.voidac.platform.fabric.mc1161.entity.Fabric1161VoidEntity;
import ac.voidac.platform.fabric.mc1161.player.Fabric1161PlatformInventory;
import ac.voidac.platform.fabric.mc1161.player.Fabric1161PlatformPlayer;
import ac.voidac.platform.fabric.mc1161.util.convert.Fabric1140ConversionUtil;
import ac.voidac.platform.fabric.mc1161.util.convert.Fabric1161MessageUtil;
import ac.voidac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.voidac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.voidac.platform.fabric.utils.message.IFabricMessageUtil;
import com.github.retrooper.packetevents.manager.server.ServerVersion;

public class VoidFabric1161LoaderPlugin extends VoidFabricLoaderPlugin {

    public VoidFabric1161LoaderPlugin() {
        this(
            new FabricPlatformPlayerFactory(
                Fabric1161PlatformPlayer::new,
                Fabric1161VoidEntity::new,
                Fabric1161PlatformInventory::new
            ),
            new Fabric1140PlatformServer(),
            new Fabric1161MessageUtil(),
            new Fabric1140ConversionUtil()
        );
    }

    protected VoidFabric1161LoaderPlugin(
            FabricPlatformPlayerFactory playerFactory,
            AbstractFabricPlatformServer platformServer,
            IFabricMessageUtil fabricMessageUtil,
            IFabricConversionUtil fabricConversionUtil
    ) {
        super(() -> new FabricParserDescriptorFactory(new FabricPlayerSelectorParser<>(Fabric1161PlayerSelectorAdapter::new)),
            playerFactory,
            platformServer,
            fabricMessageUtil,
            fabricConversionUtil
        );
    }

    @Override
    public ServerVersion getNativeVersion() {
        return ServerVersion.V_1_16_1;
    }
}
