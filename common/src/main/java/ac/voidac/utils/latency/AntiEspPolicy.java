package ac.voidac.utils.latency;

public final class AntiEspPolicy {
    private AntiEspPolicy() {}

    public static boolean shouldMaskUnderground(double viewerY, int thresholdY) {
        return !isUnderground(viewerY, thresholdY);
    }

    public static boolean isUnderground(double viewerY, int thresholdY) {
        return Math.floor(viewerY) <= thresholdY;
    }

    public static boolean resolveUndergroundState(boolean underground, double viewerY, int thresholdY) {
        return underground ? viewerY < thresholdY + 2.0 : isUnderground(viewerY, thresholdY);
    }

    public static boolean shouldMaskChunk(
            boolean terrainMasking, boolean underground, int viewerChunkX, int viewerChunkZ,
            int chunkX, int chunkZ, int revealRadius
    ) {
        return terrainMasking && (!underground
                || Math.abs(chunkX - viewerChunkX) > revealRadius
                || Math.abs(chunkZ - viewerChunkZ) > revealRadius);
    }

    public static boolean shouldRevealSection(boolean withinRealChunkWindow, boolean visibleFromPov) {
        return withinRealChunkWindow || visibleFromPov;
    }

    public static int maskedRows(int worldMinY, int sectionIndex, int thresholdY) {
        int sectionY = worldMinY + (sectionIndex << 4);
        return Math.max(0, Math.min(16, thresholdY - sectionY));
    }

    public static boolean shouldHideEntity(
            double viewerX, double viewerY, double viewerZ,
            double targetX, double targetY, double targetZ,
            int thresholdY, int proximityRadius, int lineOfSightDistance, boolean hasLineOfSight
    ) {
        if (targetY >= thresholdY) {
            return false;
        }

        double deltaX = targetX - viewerX;
        double deltaY = targetY - viewerY;
        double deltaZ = targetZ - viewerZ;
        double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        double lineOfSightSquared = (double) lineOfSightDistance * lineOfSightDistance;
        return distanceSquared > lineOfSightSquared || !hasLineOfSight;
    }

    public static boolean needsUndergroundRefresh(
            double previousY, double currentY, int thresholdY,
            int previousChunkX, int previousChunkZ, int currentChunkX, int currentChunkZ
    ) {
        boolean previousUnderground = isUnderground(previousY, thresholdY);
        boolean currentUnderground = isUnderground(currentY, thresholdY);
        return previousUnderground != currentUnderground
                || currentUnderground && (previousChunkX != currentChunkX || previousChunkZ != currentChunkZ);
    }
}
