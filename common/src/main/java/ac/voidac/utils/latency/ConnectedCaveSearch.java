package ac.voidac.utils.latency;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

final class ConnectedCaveSearch {
    private ConnectedCaveSearch() {}

    static void findVisibleSections(
            int startX, int startY, int startZ,
            int radius, int minY, int maxY, int visitLimit,
            Passability passability, LongOpenHashSet visited,
            LongArrayFIFOQueue queue, LongOpenHashSet sections
    ) {
        visited.clear();
        queue.clear();
        sections.clear();
        if (startY < minY || startY > maxY || !passability.canTraverse(startX, startY, startZ)) {
            return;
        }

        long start = blockKey(startX, startY, startZ);
        visited.add(start);
        queue.enqueue(start);
        int radiusSquared = radius * radius;

        while (queue.size() != 0 && visited.size() <= visitLimit) {
            long position = queue.dequeueLong();
            int x = blockX(position);
            int y = blockY(position);
            int z = blockZ(position);
            sections.add(sectionKey(x >> 4, y >> 4, z >> 4));

            enqueue(x + 1, y, z, startX, startY, startZ, radiusSquared, minY, maxY, visitLimit, passability, visited, queue);
            enqueue(x - 1, y, z, startX, startY, startZ, radiusSquared, minY, maxY, visitLimit, passability, visited, queue);
            enqueue(x, y + 1, z, startX, startY, startZ, radiusSquared, minY, maxY, visitLimit, passability, visited, queue);
            enqueue(x, y - 1, z, startX, startY, startZ, radiusSquared, minY, maxY, visitLimit, passability, visited, queue);
            enqueue(x, y, z + 1, startX, startY, startZ, radiusSquared, minY, maxY, visitLimit, passability, visited, queue);
            enqueue(x, y, z - 1, startX, startY, startZ, radiusSquared, minY, maxY, visitLimit, passability, visited, queue);
        }
    }

    private static void enqueue(
            int x, int y, int z, int startX, int startY, int startZ,
            int radiusSquared, int minY, int maxY, int visitLimit,
            Passability passability, LongOpenHashSet visited, LongArrayFIFOQueue queue
    ) {
        if (y < minY || y > maxY || visited.size() >= visitLimit) {
            return;
        }
        int deltaX = x - startX;
        int deltaY = y - startY;
        int deltaZ = z - startZ;
        if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > radiusSquared) {
            return;
        }

        long position = blockKey(x, y, z);
        if (!visited.add(position) || !passability.canTraverse(x, y, z)) {
            return;
        }
        queue.enqueue(position);
    }

    static long sectionKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFL) << 42
                | ((long) z & 0x3FFFFFL) << 20
                | (long) y & 0xFFFFFL;
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | (long) y & 0xFFFL;
    }

    private static int blockX(long key) {
        return (int) (key >> 38);
    }

    private static int blockY(long key) {
        return (int) (key << 52 >> 52);
    }

    private static int blockZ(long key) {
        return (int) (key << 26 >> 38);
    }

    @FunctionalInterface
    interface Passability {
        boolean canTraverse(int x, int y, int z);
    }
}
