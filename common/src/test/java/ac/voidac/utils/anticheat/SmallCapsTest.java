package ac.voidac.utils.anticheat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallCapsTest {
    @Test
    void convertsTheWholeAlphabetInBothCases() {
        assertEquals("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡxʏᴢ", SmallCaps.convert("abcdefghijklmnopqrstuvwxyz"));
        assertEquals("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡxʏᴢ", SmallCaps.convert("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    }

    @Test
    void leavesDigitsPunctuationAndAccentsUntouched() {
        assertEquals("ᴀʟᴇʀᴛѕ: 12 (è/ü)!", SmallCaps.convert("alerts: 12 (è/ü)!"));
    }

    @Test
    void onlyEnglishIsSupported() {
        assertTrue(SmallCaps.supportsLanguage("en"));
        assertTrue(SmallCaps.supportsLanguage("EN"));
        for (String language : new String[]{"it", "ru", "zh", "ja", "de", "fr", "es", "pt", "pl", "ro", "tr", "nl"}) {
            assertFalse(SmallCaps.supportsLanguage(language), language);
        }
    }

    @Test
    void tagConvertsOnlyItsContentWhenEnabled() {
        assertEquals("ᴀʟᴇʀᴛѕ Steve ᴇɴᴀʙʟᴇᴅ", render(true, "<sc>Alerts</sc> Steve <sc>enabled</sc>"));
    }

    @Test
    void tagIsTransparentWhenDisabled() {
        assertEquals("Alerts Steve enabled", render(false, "<sc>Alerts</sc> Steve <sc>enabled</sc>"));
    }

    @Test
    void tagKeepsNestedColors() {
        Component component = miniMessage(true).deserialize("<sc>hello <red>world</red></sc>").compact();
        assertEquals("ʜᴇʟʟᴏ ᴡᴏʀʟᴅ", plain(component));
        assertEquals(NamedTextColor.RED, component.children().get(0).color());
    }

    private static String render(boolean enabled, String input) {
        return plain(miniMessage(enabled).deserialize(input));
    }

    private static String plain(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof TextComponent textComponent) text.append(textComponent.content());
        component.children().forEach(child -> text.append(plain(child)));
        return text.toString();
    }

    private static MiniMessage miniMessage(boolean enabled) {
        return MiniMessage.builder()
                .tags(TagResolver.resolver(TagResolver.standard(), SmallCapsTag.resolver(() -> enabled)))
                .build();
    }
}
