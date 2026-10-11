package ac.voidac.utils.combat;

public final class SnapReturnDetector {
    private final double minSnap;
    private final double maxResidual;
    private final int maxGap;
    private double previousYaw;
    private double previousPitch;
    private long previousTick = Long.MIN_VALUE;
    private boolean previousCombat;

    public SnapReturnDetector(double minSnap, double maxResidual, int maxGap) {
        this.minSnap = minSnap;
        this.maxResidual = maxResidual;
        this.maxGap = maxGap;
    }

    public boolean accept(long tick, double deltaYaw, double deltaPitch, boolean combat) {
        double yaw = wrap(deltaYaw);
        boolean snapped = previousTick != Long.MIN_VALUE
                && tick - previousTick <= maxGap
                && (combat || previousCombat)
                && Math.hypot(previousYaw, previousPitch) >= minSnap
                && Math.hypot(wrap(yaw + previousYaw), deltaPitch + previousPitch) <= maxResidual;
        previousYaw = yaw;
        previousPitch = deltaPitch;
        previousTick = snapped ? Long.MIN_VALUE : tick;
        previousCombat = combat;
        return snapped;
    }

    public void reset() {
        previousTick = Long.MIN_VALUE;
    }

    private static double wrap(double degrees) {
        double wrapped = (degrees + 180) % 360;
        if (wrapped < 0) wrapped += 360;
        return wrapped - 180;
    }
}
