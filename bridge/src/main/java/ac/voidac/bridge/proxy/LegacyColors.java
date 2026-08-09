package ac.voidac.bridge.proxy;

import org.jetbrains.annotations.NotNull;

/** Turns &amp; colour codes into section signs before either proxy's serialiser sees them. */
public final class LegacyColors {

    private LegacyColors() {
    }

    private static final String CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    /** Only converts an ampersand that's actually a colour code, so "Cheating &amp; evading" survives. */
    public static @NotNull String translate(@NotNull String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] != '&') continue;
            if (CODES.indexOf(chars[i + 1]) < 0) continue;
            chars[i] = '§';
            i++; // skip the code char so "&&a" isn't read as two
        }
        return new String(chars);
    }
}
