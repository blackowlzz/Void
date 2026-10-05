package ac.voidac.utils.anticheat;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class SmallCaps {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final String GLYPHS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡxʏᴢ";

    public boolean supportsLanguage(@NotNull String languageCode) {
        return languageCode.equalsIgnoreCase("en");
    }

    public @NotNull String convert(@NotNull String input) {
        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            int index = ALPHABET.indexOf(Character.toLowerCase(character));
            result.append(index < 0 || character > 'z' ? character : GLYPHS.charAt(index));
        }
        return result.toString();
    }
}
