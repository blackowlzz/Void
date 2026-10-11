package ac.voidac.utils.combat;

public final class SensitivityGrid {
    private static final double BASE_TOLERANCE = 0.001;

    private SensitivityGrid() {
    }

    public static boolean isOffGrid(double delta, double step, double magnitude) {
        double steps = Math.abs(delta) / step;
        double residual = Math.abs(steps - Math.rint(steps)) * step;
        return residual > BASE_TOLERANCE + 4 * Math.ulp((float) magnitude);
    }
}
