package ac.voidac.utils.anticheat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseStatusTest {
    private static final List<String> RELEASES = List.of("2.8.0", "2.7.1", "2.7.0", "2.6.9", "2.6.4");

    @Test
    void reportsUpToDateOnTheLatestRelease() {
        ReleaseStatus status = ReleaseStatus.evaluate("2.8.0", RELEASES);
        assertEquals(ReleaseStatus.Kind.UP_TO_DATE, status.kind());
        assertEquals(0, status.versionsBehind());
    }

    @Test
    void countsEveryNewerRelease() {
        ReleaseStatus status = ReleaseStatus.evaluate("2.7.0", RELEASES);
        assertEquals(ReleaseStatus.Kind.BEHIND, status.kind());
        assertEquals(2, status.versionsBehind());
        assertEquals("2.8.0", status.latest());
    }

    @Test
    void reportsDevelopmentBuildsAsAhead() {
        assertEquals(ReleaseStatus.Kind.AHEAD, ReleaseStatus.evaluate("2.9.0-SNAPSHOT", RELEASES).kind());
    }

    @Test
    void reportsUnknownWhenNothingCanBeCompared() {
        assertEquals(ReleaseStatus.Kind.UNKNOWN, ReleaseStatus.evaluate("2.8.0", List.of()).kind());
        assertEquals(ReleaseStatus.Kind.UNKNOWN, ReleaseStatus.evaluate("unknown", RELEASES).kind());
    }

    @Test
    void comparesVersionsNumerically() {
        assertEquals(OptionalInt.of(-1), SemanticVersion.compare("2.9.0", "2.10.0"));
        assertEquals(OptionalInt.of(0), SemanticVersion.compare("2.8", "2.8.0+build.5"));
        assertEquals(OptionalInt.empty(), SemanticVersion.compare("abc", "2.8.0"));
    }
}
