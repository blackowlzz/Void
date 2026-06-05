package ac.voidac.manager.player.features.types;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.api.feature.FeatureState;
import ac.voidac.player.VoidPlayer;

public class ExemptElytraFeature implements VoidFeature {

    @Override
    public String getName() {
        return "ExemptElytra";
    }

    @Override
    public void setState(VoidPlayer player, ConfigManager config, FeatureState state) {
        switch (state) {
            case ENABLED -> player.setExemptElytra(true);
            case DISABLED -> player.setExemptElytra(false);
            default -> player.setExemptElytra(isEnabledInConfig(player, config));
        }
    }

    @Override
    public boolean isEnabled(VoidPlayer player) {
        return player.isExemptElytra();
    }

    @Override
    public boolean isEnabledInConfig(VoidPlayer player, ConfigManager config) {
        return config.getBooleanElse("exempt-elytra", false);
    }

}
