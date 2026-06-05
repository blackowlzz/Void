package ac.voidac.platform.fabric.mc1161;

import ac.voidac.platform.api.sender.Sender;
import ac.voidac.platform.fabric.AbstractFabricPlatformServer;
import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import net.minecraft.commands.CommandSourceStack;

public class Fabric1140PlatformServer extends AbstractFabricPlatformServer {

    @Override
    public void dispatchCommand(Sender sender, String command) {
        CommandSourceStack commandSource = VoidFabricLoaderPlugin.LOADER.getFabricSenderFactory().unwrap(sender);
        VoidFabricLoaderPlugin.FABRIC_SERVER.getCommands().performCommand(commandSource, command);
    }

    // TODO (Cross-platform) implement proper bukkit equivalent for getting TPS over time
    @Override
    public double getTPS() {
        return Math.min(1000.0 / VoidFabricLoaderPlugin.FABRIC_SERVER.getAverageTickTime(), 20.0);
    }
}
