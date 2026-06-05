package ac.voidac.platform.fabric.mc1205;

import ac.voidac.platform.api.sender.Sender;
import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.mc1194.Fabric1190PlatformServer;
import net.minecraft.commands.CommandSourceStack;

public class Fabric1203PlatformServer extends Fabric1190PlatformServer {

    // TODO (Cross-platform) implement proper bukkit equivalent for getting TPS over time
    @Override
    public double getTPS() {
        return Math.min(1000.0 / VoidFabricLoaderPlugin.FABRIC_SERVER.getCurrentSmoothedTickTime(), VoidFabricLoaderPlugin.FABRIC_SERVER.tickRateManager().tickrate());
    }

    // Return type changed from int -> void in 1.20.3
    @Override
    public void dispatchCommand(Sender sender, String command) {
        CommandSourceStack commandSource = VoidFabricLoaderPlugin.LOADER.getFabricSenderFactory().unwrap(sender);
        VoidFabricLoaderPlugin.FABRIC_SERVER.getCommands().performPrefixedCommand(commandSource, command);
    }
}
