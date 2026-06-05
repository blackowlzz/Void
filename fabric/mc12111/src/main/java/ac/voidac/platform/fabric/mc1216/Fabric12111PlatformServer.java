package ac.voidac.platform.fabric.mc1216;

import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.mc1205.Fabric1203PlatformServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public class Fabric12111PlatformServer extends Fabric1203PlatformServer {

    @Override
    public int getOperatorPermissionLevel() {
        return VoidFabricLoaderPlugin.FABRIC_SERVER.operatorUserPermissions().level().id();
    }

    @Override
    public boolean hasPermission(CommandSourceStack stack, int level) {
        return stack.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.byId(level))
        );
    }
}
