package ac.voidac.manager.player.features.types;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.api.feature.FeatureState;
import ac.voidac.player.VoidPlayer;

public class ExperimentalChecksFeature implements VoidFeature {

    @Override
    public String getName() {
        return "ExperimentalChecks";
    }

    @Override
    public void setState(VoidPlayer player, ConfigManager config, FeatureState state) {
        switch (state) {
            case ENABLED -> player.setExperimentalChecks(true);
            case DISABLED -> player.setExperimentalChecks(false);
            default -> player.setExperimentalChecks(isEnabledInConfig(player, config));
        }
    }

    @Override
    public boolean isEnabled(VoidPlayer player) {
        return player.isExperimentalChecks();
    }

    @Override
    public boolean isEnabledInConfig(VoidPlayer player, ConfigManager config) {
        return config.getBooleanElse("experimental-checks", false);
    }

}
