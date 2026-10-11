package ac.voidac.utils.combat;

public final class StepEstimator {
    private static final int CAPACITY = 64;
    private static final int MIN_SAMPLES = 24;
    private static final int MIN_FINE_SAMPLES = 8;
    private static final int FINE_MULTIPLE = 4;
    private static final int MIN_DISTINCT_MULTIPLES = 3;
    private static final double MIN_STEP = 0.0096;
    private static final double MAX_STEP = 0.62;
    private static final double MIN_DELTA = 0.05;
    private static final double MAX_DELTA = 6;
    private static final double MAX_FINE_RMS = 0.0004;
    private static final double MAX_FIT_RMS = 0.0005;
    private static final double FIT_TOLERANCE = 0.002;
    private static final double RECENT_TOLERANCE = 0.0015;
    private static final int RECENT = 16;
    private static final int MIN_RECENT_FITS = 14;

    private final double[] samples = new double[CAPACITY];
    private int count;
    private int next;
    private double locked;
    private boolean fresh;
    private final BooleanWindow recentFits = new BooleanWindow(RECENT);

    public void observe(double delta, double seed) {
        double magnitude = Math.abs(delta);
        if (magnitude < MIN_DELTA || magnitude > MAX_DELTA) return;

        recentFits.add(locked > 0 && Math.abs(magnitude - Math.rint(magnitude / locked) * locked) <= RECENT_TOLERANCE);
        samples[next] = magnitude;
        next = (next + 1) % CAPACITY;
        if (count < CAPACITY) count++;
        if (count < MIN_SAMPLES) {
            fresh = false;
            return;
        }

        boolean fitted = locked > 0 && refit(locked) || seed > 0 && refit(seed);
        fresh = fitted && recentFits.isFull() && recentFits.trueCount() >= MIN_RECENT_FITS;
    }

    public boolean isFresh() {
        return fresh;
    }

    public double step() {
        return locked;
    }

    public void reset() {
        count = 0;
        next = 0;
        locked = 0;
        fresh = false;
        recentFits.clear();
    }

    private boolean refit(double reference) {
        if (reference < MIN_STEP || reference > MAX_STEP) return false;

        double coarse = fit(reference, Double.MAX_VALUE, FINE_MULTIPLE, MAX_FINE_RMS, MIN_FINE_SAMPLES);
        if (coarse < MIN_STEP || coarse > MAX_STEP) return false;

        double refined = fit(coarse, FIT_TOLERANCE, Integer.MAX_VALUE, MAX_FIT_RMS, MIN_FINE_SAMPLES);
        if (refined <= 0) return false;

        locked = refined;
        return true;
    }

    private double fit(double reference, double tolerance, int maxMultiple, double maxRms, int minUsed) {
        double numerator = 0;
        double denominator = 0;
        int used = 0;
        long multiples = 0;
        for (int i = 0; i < count; i++) {
            double multiple = Math.rint(samples[i] / reference);
            if (multiple < 1 || multiple > maxMultiple) continue;
            if (Math.abs(samples[i] - multiple * reference) > tolerance) continue;
            numerator += samples[i] * multiple;
            denominator += multiple * multiple;
            used++;
            if (multiple < Long.SIZE) multiples |= 1L << (int) multiple;
        }
        if (used < minUsed || Long.bitCount(multiples) < MIN_DISTINCT_MULTIPLES) return 0;

        double step = numerator / denominator;
        double squares = 0;
        for (int i = 0; i < count; i++) {
            double multiple = Math.rint(samples[i] / reference);
            if (multiple < 1 || multiple > maxMultiple) continue;
            if (Math.abs(samples[i] - multiple * reference) > tolerance) continue;
            double residual = samples[i] - multiple * step;
            squares += residual * residual;
        }
        return Math.sqrt(squares / used) <= maxRms ? step : 0;
    }
}
