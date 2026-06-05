package ac.voidac.platform.fabric.manager;

import ac.voidac.platform.api.manager.PermissionRegistrationManager;
import ac.voidac.platform.api.permissions.PermissionDefaultValue;
import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.sender.FabricSenderFactory;
import me.lucko.fabric.api.permissions.v0.Permissions;

import static ac.voidac.platform.fabric.sender.FabricSenderFactory.HAS_PERMISSIONS_API;

public class FabricPermissionRegistrationManager implements PermissionRegistrationManager {

    private final FabricSenderFactory fabricSenderFactory = VoidFabricLoaderPlugin.LOADER.getFabricSenderFactory();

    public FabricPermissionRegistrationManager() {
        registerPermission("void.exempt", PermissionDefaultValue.FALSE);
        registerPermission("void.nosetback", PermissionDefaultValue.FALSE);
        registerPermission("void.nomodifypacket", PermissionDefaultValue.FALSE);
        registerPermission("void.nosetback", PermissionDefaultValue.FALSE);
        registerPermission("void.storageesp", PermissionDefaultValue.FALSE);
        registerPermission("void.alerts.enable-on-join", PermissionDefaultValue.FALSE);
        registerPermission("void.verbose.enable-on-join", PermissionDefaultValue.FALSE);
        registerPermission("void.brand.enable-on-join", PermissionDefaultValue.FALSE);
        registerPermission("void.alerts.enable-on-join.silent", PermissionDefaultValue.FALSE);
        registerPermission("void.verbose.enable-on-join.silent", PermissionDefaultValue.FALSE);
        registerPermission("void.brand.enable-on-join.silent", PermissionDefaultValue.FALSE);
    }

    @Override
    public void registerPermission(String name, PermissionDefaultValue defaultValue) {
        fabricSenderFactory.registerPermissionDefault(name, defaultValue);
        if (HAS_PERMISSIONS_API)
            Permissions.check(VoidFabricLoaderPlugin.FABRIC_SERVER.createCommandSourceStack(), name);
    }
}
