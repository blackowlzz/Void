package ac.voidac.utils.viaversion;

import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.reflection.ReflectionUtils;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ViaVersionUtil {
    public static final boolean isAvailable = ReflectionUtils.hasClass("com.viaversion.viaversion.api.Via");

    static {
        if (!isAvailable && ReflectionUtils.hasClass("us.myles.ViaVersion.api.Via")) {
            LogUtil.error("Using unsupported ViaVersion 4.0 API, update ViaVersion to 5.0");
        }
    }

    public static void injectHooks() {
        if (isAvailable) ViaVersionHooks.load();
    }
}
