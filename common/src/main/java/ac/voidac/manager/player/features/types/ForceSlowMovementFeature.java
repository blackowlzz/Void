package ac.voidac.manager.player.features.types;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.api.feature.FeatureState;
import ac.voidac.player.VoidPlayer;

public class ForceSlowMovementFeature implements VoidFeature {

    @Override
    public String getName() {
        return "ForceSlowMovement";
    }

    @Override
    public void setState(VoidPlayer player, ConfigManager config, FeatureState state) {
        switch (state) {
            case ENABLED -> player.setForceSlowMovement(true);
            case DISABLED -> player.setForceSlowMovement(false);
            default -> player.setForceSlowMovement(isEnabledInConfig(player, config));
        }
    }

    @Override
    public boolean isEnabled(VoidPlayer player) {
        return player.isForceSlowMovement();
    }

    @Override
    public boolean isEnabledInConfig(VoidPlayer player, ConfigManager config) {
        return config.getBooleanElse("force-slow-movement", true);
    }

}
