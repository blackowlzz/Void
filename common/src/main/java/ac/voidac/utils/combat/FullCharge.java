package ac.voidac.utils.combat;

public final class FullCharge {
    private static final double EPSILON = 1e-4;

    private FullCharge() {
    }

    public static int ticks(double attackSpeed) {
        return attackSpeed <= 0 ? 0 : (int) Math.ceil(20.0 / attackSpeed - 0.5 - EPSILON);
    }
}
