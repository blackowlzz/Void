package ac.voidac.checks;

import ac.voidac.VoidAPI;
import ac.voidac.api.AbstractProcessor;
import ac.voidac.api.config.ConfigReloadable;
import ac.voidac.utils.common.ConfigReloadObserver;

public abstract class VoidProcessor implements AbstractProcessor, ConfigReloadable, ConfigReloadObserver {

    // Not everything has to be a check for it to process packets & be configurable

    @Override
    public void reload() {
        reload(VoidAPI.INSTANCE.getConfigManager().getConfig());
    }

}
