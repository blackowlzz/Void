package ac.voidac.utils.data;

public final class CoordinateOffsetTolerance {
    public static final double HORIZONTAL = 1e-6;

    private CoordinateOffsetTolerance() {
    }

    public static boolean horizontalMatch(double expected, double actual, double threshold, boolean coordinateOffsetCompat) {
        if (!coordinateOffsetCompat) return Math.abs(expected - actual) <= threshold;
        return Math.abs(expected - actual) <= Math.max(threshold, HORIZONTAL);
    }
}
