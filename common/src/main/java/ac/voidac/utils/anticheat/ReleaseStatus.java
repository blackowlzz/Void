package ac.voidac.utils.anticheat;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.OptionalInt;

public record ReleaseStatus(@NotNull Kind kind, int versionsBehind, @NotNull String latest) {

    public enum Kind {
        UP_TO_DATE,
        BEHIND,
        AHEAD,
        UNKNOWN
    }

    public static final ReleaseStatus UNKNOWN = new ReleaseStatus(Kind.UNKNOWN, 0, "");

    public static @NotNull ReleaseStatus evaluate(@NotNull String current, @NotNull List<String> releasesNewestFirst) {
        if (releasesNewestFirst.isEmpty()) return UNKNOWN;
        String latest = releasesNewestFirst.get(0);
        int behind = 0;
        for (String release : releasesNewestFirst) {
            OptionalInt comparison = SemanticVersion.compare(current, release);
            if (comparison.isEmpty()) return UNKNOWN;
            if (comparison.getAsInt() < 0) behind++;
        }
        if (behind > 0) return new ReleaseStatus(Kind.BEHIND, behind, latest);
        boolean ahead = SemanticVersion.compare(current, latest).orElse(0) > 0;
        return new ReleaseStatus(ahead ? Kind.AHEAD : Kind.UP_TO_DATE, 0, latest);
    }
}
