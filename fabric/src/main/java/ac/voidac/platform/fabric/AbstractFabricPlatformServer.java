package ac.voidac.platform.fabric;

import ac.voidac.platform.api.PlatformServer;
import ac.voidac.platform.api.sender.Sender;
import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractFabricPlatformServer implements PlatformServer {

    public int getOperatorPermissionLevel() {
        return VoidFabricLoaderPlugin.FABRIC_SERVER.getOperatorUserPermissionLevel();
    }

    public boolean hasPermission(CommandSourceStack stack, int level) {
        return stack.hasPermission(level);
    }

    @Override
    public String getPlatformImplementationString() {
        // Return the Fabric server version
        return "Fabric " + FabricLoader.getInstance().getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString() + " (MC: " + VoidFabricLoaderPlugin.FABRIC_SERVER.getServerVersion() + ")";
    }

    @Override
    public Sender getConsoleSender() {
        CommandSourceStack consoleSource = VoidFabricLoaderPlugin.FABRIC_SERVER.createCommandSourceStack();
        return VoidFabricLoaderPlugin.LOADER.getFabricSenderFactory().wrap(consoleSource);
    }

    @Override
    public void registerOutgoingPluginChannel(String name) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    public GameProfile getProfileByName(String name) {
        return VoidFabricLoaderPlugin.FABRIC_SERVER.getProfileCache().get(name);
    }
}
