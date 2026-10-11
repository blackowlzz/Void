package ac.voidac.utils.data;

public final class CoordinateShift {
    private static final double INTEGER_TOLERANCE = 1e-3;

    private double rawX = Double.NaN;
    private double rawZ = Double.NaN;
    private int shiftX;
    private int shiftZ;

    public void capture(double rawX, double rawZ) {
        this.rawX = rawX;
        this.rawZ = rawZ;
    }

    public void learn(double realX, double realZ) {
        double diffX = rawX - realX;
        double diffZ = rawZ - realZ;
        rawX = Double.NaN;
        rawZ = Double.NaN;
        if (!isNearInteger(diffX) || !isNearInteger(diffZ)) return;
        shiftX = (int) Math.rint(diffX);
        shiftZ = (int) Math.rint(diffZ);
    }

    public double toRealX(double rawX) {
        return rawX - shiftX;
    }

    public double toRealZ(double rawZ) {
        return rawZ - shiftZ;
    }

    public int toRealBlockX(int rawBlockX) {
        return rawBlockX - shiftX;
    }

    public int toRealBlockZ(int rawBlockZ) {
        return rawBlockZ - shiftZ;
    }

    private static boolean isNearInteger(double value) {
        return Double.isFinite(value) && Math.abs(value - Math.rint(value)) <= INTEGER_TOLERANCE;
    }
}
