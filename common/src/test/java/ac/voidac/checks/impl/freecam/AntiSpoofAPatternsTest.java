package ac.voidac.checks.impl.freecam;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AntiSpoofAPatternsTest {

    @Test
    void normalizesConfiguredPatterns() {
        List<String> configured = Arrays.asList(" Meteor-Client ", "WURST:", "", "   ", null, "wurst:");

        assertArrayEquals(new String[]{"meteor-client", "wurst:"}, AntiSpoofA.normalizePatterns(configured));
    }

    @Test
    void emptyListDisablesMatching() {
        assertArrayEquals(new String[0], AntiSpoofA.normalizePatterns(List.of()));
    }
}
