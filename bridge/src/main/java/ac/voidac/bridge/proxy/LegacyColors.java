package ac.voidac.bridge.proxy;

import org.jetbrains.annotations.NotNull;

public final class LegacyColors {
    private LegacyColors() {
    }

    private static final String CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    public static @NotNull String translate(@NotNull String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] != '&') continue;
            if (CODES.indexOf(chars[i + 1]) < 0) continue;
            chars[i] = '§';
            i++;
        }
        return new String(chars);
    }
}
