package ac.voidac.api.plugin;

import java.io.File;
import java.util.logging.Logger;

public interface VoidPlugin {

    VoidPluginDescription getDescription();

    Logger getLogger();

    File getDataFolder();
}
