package ac.voidac.manager.player.features;

import ac.voidac.manager.player.features.types.VoidFeature;
import ac.voidac.utils.anticheat.LogUtil;
import com.google.common.collect.ImmutableMap;

import java.util.regex.Pattern;

public class FeatureBuilder {

    private static final Pattern VALID = Pattern.compile("[a-zA-Z0-9_]{1,64}");
    private final ImmutableMap.Builder<String, VoidFeature> mapBuilder = ImmutableMap.builder();

    public <T extends VoidFeature> void register(T feature) {
        if (!VALID.matcher(feature.getName()).matches()) {
            LogUtil.error("Invalid feature name: " + feature.getName());
            return;
        }
        mapBuilder.put(feature.getName(), feature);
    }

    public ImmutableMap<String, VoidFeature> buildMap() {
        return mapBuilder.build();
    }

}
