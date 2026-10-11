package ac.voidac.utils.latency;

public final class AntiEspPolicy {
    private AntiEspPolicy() {}

    public static int maskedRows(int worldMinY, int sectionIndex, int thresholdY) {
        int sectionY = worldMinY + (sectionIndex << 4);
        return Math.max(0, Math.min(16, thresholdY - sectionY));
    }
}
