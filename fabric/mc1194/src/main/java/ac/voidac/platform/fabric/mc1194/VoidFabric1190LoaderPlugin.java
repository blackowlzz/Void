package ac.voidac.platform.fabric.mc1194;

import ac.voidac.platform.fabric.AbstractFabricPlatformServer;
import ac.voidac.platform.api.manager.CommandAdapter;
import ac.voidac.platform.fabric.mc1161.command.Fabric1161PlayerSelectorAdapter;
import ac.voidac.platform.fabric.command.FabricPlayerSelectorParser;
import ac.voidac.platform.fabric.manager.FabricParserDescriptorFactory;
import ac.voidac.platform.fabric.mc1171.VoidFabric1170LoaderPlugin;
import ac.voidac.platform.fabric.mc1171.player.Fabric1170PlatformPlayer;
import ac.voidac.platform.fabric.mc1194.convert.Fabric1190MessageUtil;
import ac.voidac.platform.fabric.mc1194.entity.Fabric1194VoidEntity;
import ac.voidac.platform.fabric.mc1194.player.Fabric1193PlatformInventory;
import ac.voidac.platform.fabric.mc1161.player.Fabric1161PlatformInventory;
import ac.voidac.platform.fabric.mc1161.util.convert.Fabric1140ConversionUtil;
import ac.voidac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.voidac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.voidac.platform.fabric.utils.message.IFabricMessageUtil;
import ac.voidac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;


public class VoidFabric1190LoaderPlugin extends VoidFabric1170LoaderPlugin {

    public VoidFabric1190LoaderPlugin() {
        this(
                () -> new FabricParserDescriptorFactory(
                    new FabricPlayerSelectorParser<>(Fabric1161PlayerSelectorAdapter::new)
            ),
            new FabricPlatformPlayerFactory(
                    Fabric1170PlatformPlayer::new,
                    Fabric1194VoidEntity::new,
                    PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_19_2)
                            ? Fabric1193PlatformInventory::new : Fabric1161PlatformInventory::new
            ),
            new Fabric1190PlatformServer(),
            new Fabric1190MessageUtil(),
            new Fabric1140ConversionUtil()
        );
    }

    protected VoidFabric1190LoaderPlugin(
            LazyHolder<CommandAdapter> parserDescriptorFactory,
            FabricPlatformPlayerFactory platformPlayerFactory,
            AbstractFabricPlatformServer platformServer,
            IFabricMessageUtil fabricMessageUtil,
            IFabricConversionUtil fabricConversionUtil) {
        super(parserDescriptorFactory, platformPlayerFactory, platformServer, fabricMessageUtil, fabricConversionUtil);
    }

    @Override
    public ServerVersion getNativeVersion() {
        return ServerVersion.V_1_19_4;
    }
}
