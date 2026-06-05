package ac.voidac.manager.player.features.types;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.api.feature.FeatureState;
import ac.voidac.player.VoidPlayer;

public interface VoidFeature {
    String getName();

    void setState(VoidPlayer player, ConfigManager config, FeatureState state);

    boolean isEnabled(VoidPlayer player);

    boolean isEnabledInConfig(VoidPlayer player, ConfigManager config);
}
