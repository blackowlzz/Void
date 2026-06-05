package ac.voidac.utils.common;


import ac.voidac.api.config.ConfigManager;

public interface ConfigReloadObserver {

    void onReload(ConfigManager config);

}
