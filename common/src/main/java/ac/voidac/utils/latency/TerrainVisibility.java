package ac.voidac.utils.latency;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.LongConsumer;

final class TerrainVisibility {
    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, -1, 1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, -1, 1};
    private static final double EPSILON = 1.0E-7;
    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet reached = new LongOpenHashSet();
    private final ArrayDeque<Region> queue = new ArrayDeque<>();
    private final LongOpenHashSet newlyVisible = new LongOpenHashSet();
    private final LongOpenHashSet changed = new LongOpenHashSet();
    private final long[] visitedBlocks = new long[64];
    private final long[] originBlocks = new long[64];
    private Section originSection;
    private Region cachedOrigin;
    private final int[] blockQueue = new int[4096];
    private final long[] componentFaces = new long[24];
    private final int[] portalBounds = new int[4];
    private final double[] projection = new double[4];
    private final double[] polygonA = new double[48];
    private final double[] polygonB = new double[48];
    private long epoch;

    void putUniform(int x, int y, int z, boolean open) {
        long key = key(x, y, z);
        Section previous = sections.get(key);
        Section section = new Section();
        section.visible = previous != null && previous.visible;
        section.uniform = true;
        if (open) {
            Arrays.fill(section.open, -1L);
            long[] faces = new long[24];
            Arrays.fill(faces, -1L);
            section.regions = new Region[]{new Region(key, 0, faces)};
        } else {
            section.regions = new Region[0];
        }
        sections.put(key, section);
        changed.add(key);
    }

    void put(int x, int y, int z, OpenBlocks blocks) {
        long key = key(x, y, z);
        Section previous = sections.get(key);
        Section section = new Section();
        section.visible = previous != null && previous.visible;
        for (int index = 0; index < 4096; index++) {
            if (blocks.isOpen(index & 15, index >> 8, index >> 4 & 15)) {
                section.open[index >> 6] |= 1L << (index & 63);
            }
        }
        Arrays.fill(visitedBlocks, 0);
        var regions = new ArrayList<Region>();
        for (int index = 0; index < 4096; index++) {
            if (!section.isOpen(index) || visited(index)) {
                continue;
            }
            Arrays.fill(componentFaces, 0);
            flood(section, index, componentFaces);
            boolean hasPortal = false;
            for (long face : componentFaces) {
                hasPortal |= face != 0;
            }
            if (!hasPortal) {
                continue;
            }
            if (section.merged) {
                for (int face = 0; face < 24; face++) {
                    regions.get(0).faces[face] |= componentFaces[face];
                }
            } else if (regions.size() == 64) {
                Region merged = regions.get(0);
                for (Region region : regions) {
                    for (int face = 0; face < 24; face++) {
                        merged.faces[face] |= region.faces[face] | componentFaces[face];
                    }
                }
                regions.clear();
                regions.add(merged);
                section.merged = true;
            } else {
                regions.add(new Region(key, index, componentFaces.clone()));
            }
        }
        section.regions = regions.toArray(new Region[0]);
        sections.put(key, section);
        changed.add(key);
    }

    private void flood(Section section, int start, long[] faces) {
        int head = 0;
        int tail = 1;
        blockQueue[0] = start;
        visitedBlocks[start >> 6] |= 1L << (start & 63);
        while (head < tail) {
            int index = blockQueue[head++];
            int x = index & 15;
            int y = index >> 8;
            int z = index >> 4 & 15;
            if (faces != null) {
                if (x == 0) setFace(faces, 0, y, z);
                if (x == 15) setFace(faces, 1, y, z);
                if (y == 0) setFace(faces, 2, x, z);
                if (y == 15) setFace(faces, 3, x, z);
                if (z == 0) setFace(faces, 4, x, y);
                if (z == 15) setFace(faces, 5, x, y);
            }
            if (x > 0) tail = visit(section, index - 1, tail);
            if (x < 15) tail = visit(section, index + 1, tail);
            if (y > 0) tail = visit(section, index - 256, tail);
            if (y < 15) tail = visit(section, index + 256, tail);
            if (z > 0) tail = visit(section, index - 16, tail);
            if (z < 15) tail = visit(section, index + 16, tail);
        }
    }

    private int visit(Section section, int index, int tail) {
        if (section.isOpen(index) && !visited(index)) {
            visitedBlocks[index >> 6] |= 1L << (index & 63);
            blockQueue[tail++] = index;
        }
        return tail;
    }

    private boolean visited(int index) {
        return (visitedBlocks[index >> 6] & 1L << (index & 63)) != 0;
    }

    private static void setFace(long[] faces, int face, int a, int b) {
        int bit = a << 4 | b;
        faces[(face << 2) | (bit >> 6)] |= 1L << (bit & 63);
    }

    private Region originRegion(Section section, double x, double y, double z) {
        if (section == null || section.regions.length == 0) {
            return null;
        }
        int index = ((int) Math.floor(y) & 15) << 8 | ((int) Math.floor(z) & 15) << 4 | (int) Math.floor(x) & 15;
        if (!section.isOpen(index)) {
            return null;
        }
        if (section.merged || section.uniform) {
            return section.regions[0];
        }
        if (section == originSection && (originBlocks[index >> 6] & 1L << (index & 63)) != 0) {
            return cachedOrigin;
        }
        Arrays.fill(visitedBlocks, 0);
        flood(section, index, null);
        originSection = section;
        cachedOrigin = null;
        System.arraycopy(visitedBlocks, 0, originBlocks, 0, visitedBlocks.length);
        for (Region region : section.regions) {
            if (visited(region.sample)) {
                cachedOrigin = region;
                break;
            }
        }
        return cachedOrigin;
    }

    void reveal(int playerX, int playerY, int playerZ, int horizontalRadius, int thresholdY) {
        revealFrom((playerX << 4) + 8.5, (playerY << 4) + 8.5, (playerZ << 4) + 8.5,
                horizontalRadius, thresholdY);
    }

    void revealFrom(double eyeX, double eyeY, double eyeZ, int horizontalRadius, int thresholdY) {
        int playerX = (int) Math.floor(eyeX) >> 4;
        int playerY = (int) Math.floor(eyeY) >> 4;
        int playerZ = (int) Math.floor(eyeZ) >> 4;
        reached.clear();
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    show(key(playerX + dx, playerY + dy, playerZ + dz));
                }
            }
        }
        var changes = changed.iterator();
        while (changes.hasNext()) {
            long key = changes.nextLong();
            if (((long) y(key) << 4) + 15 >= thresholdY) {
                show(key);
            }
        }
        long origin = key(playerX, playerY, playerZ);
        reached.add(origin);
        Region start = originRegion(sections.get(origin), eyeX, eyeY, eyeZ);
        if (start != null) {
            for (int view = 0; view < 6; view++) {
                boolean above = eyeY >= thresholdY;
                if (above && view == 3) {
                    continue;
                }
                epoch++;
                queue.clear();
                accept(start, -1, above && view < 2 ? 0 : 1, -1, above && view >= 4 ? 0 : 1);
                while (!queue.isEmpty()) {
                    Region region = queue.remove();
                    long key = region.sectionKey;
                    region.queued = false;
                    reached.add(key);
                    show(key);
                    int x = x(key);
                    int y = y(key);
                    int z = z(key);
                    for (int face = 0; face < 6; face++) {
                        Section neighbor = sections.get(key(x + DX[face], y + DY[face], z + DZ[face]));
                        if (neighbor == null) {
                            continue;
                        }
                        for (Region other : neighbor.regions) {
                            if (!portal(region, face, other) || !project(face, view, x, y, z, eyeX, eyeY, eyeZ)) {
                                continue;
                            }
                            double minU = Math.max(region.minU, projection[0]);
                            double maxU = Math.min(region.maxU, projection[1]);
                            double minV = Math.max(region.minV, projection[2]);
                            double maxV = Math.min(region.maxV, projection[3]);
                            if (minU <= maxU + EPSILON && minV <= maxV + EPSILON) {
                                accept(other, minU, maxU, minV, maxV);
                            }
                        }
                    }
                }
            }
        }
        var iterator = reached.iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        show(key(x(key) + dx, y(key) + dy, z(key) + dz));
                    }
                }
            }
        }
        changed.clear();
    }

    private boolean portal(Region region, int face, Region other) {
        int minA = 16;
        int maxA = 0;
        int minB = 16;
        int maxB = 0;
        for (int i = 0; i < 4; i++) {
            long bits = region.faces[(face << 2) | i] & other.faces[((face ^ 1) << 2) | i];
            while (bits != 0) {
                int bit = (i << 6) + Long.numberOfTrailingZeros(bits);
                int a = bit >> 4;
                int b = bit & 15;
                minA = Math.min(minA, a);
                maxA = Math.max(maxA, a + 1);
                minB = Math.min(minB, b);
                maxB = Math.max(maxB, b + 1);
                bits &= bits - 1;
            }
        }
        portalBounds[0] = minA;
        portalBounds[1] = maxA;
        portalBounds[2] = minB;
        portalBounds[3] = maxB;
        return minA < maxA;
    }

    private boolean project(int face, int view, int x, int y, int z,
                            double eyeX, double eyeY, double eyeZ) {
        int axis = face >> 1;
        double plane = ((axis == 0 ? x : axis == 1 ? y : z) << 4) + (face & 1) * 16.0;
        double eye = axis == 0 ? eyeX : axis == 1 ? eyeY : eyeZ;
        if (((face & 1) == 0 ? eye - plane : plane - eye) < -EPSILON) {
            return false;
        }
        if (Math.abs(plane - eye) <= EPSILON) {
            plane += ((face & 1) == 0 ? -1 : 1) * EPSILON * 2;
        }
        double minA = portalBounds[0];
        double maxA = portalBounds[1];
        double minB = portalBounds[2];
        double maxB = portalBounds[3];
        int viewAxis = view >> 1;
        double sign = (view & 1) == 0 ? -1 : 1;
        for (int corner = 0; corner < 4; corner++) {
            double a = corner == 0 || corner == 3 ? minA : maxA;
            double b = corner < 2 ? minB : maxB;
            double px = (axis == 0 ? plane : (x << 4) + a) - eyeX;
            double py = (axis == 1 ? plane : (y << 4) + (axis == 0 ? a : b)) - eyeY;
            double pz = (axis == 2 ? plane : (z << 4) + b) - eyeZ;
            polygonA[corner * 3] = (viewAxis == 0 ? px : viewAxis == 1 ? py : pz) * sign;
            polygonA[corner * 3 + 1] = viewAxis == 0 ? py : px;
            polygonA[corner * 3 + 2] = viewAxis == 2 ? py : pz;
        }
        double[] input = polygonA;
        double[] output = polygonB;
        int count = 4;
        for (int clippingPlane = 0; clippingPlane < 5; clippingPlane++) {
            count = clip(input, count, output, clippingPlane);
            if (count == 0) {
                return false;
            }
            double[] swap = input;
            input = output;
            output = swap;
        }
        projection[0] = projection[2] = Double.POSITIVE_INFINITY;
        projection[1] = projection[3] = Double.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < count; vertex++) {
            double depth = input[vertex * 3];
            double u = input[vertex * 3 + 1] / depth;
            double v = input[vertex * 3 + 2] / depth;
            projection[0] = Math.min(projection[0], u);
            projection[1] = Math.max(projection[1], u);
            projection[2] = Math.min(projection[2], v);
            projection[3] = Math.max(projection[3], v);
        }
        return true;
    }

    private static int clip(double[] input, int count, double[] output, int plane) {
        int written = 0;
        int previous = (count - 1) * 3;
        double previousDistance = planeDistance(input, previous, plane);
        for (int vertex = 0; vertex < count; vertex++) {
            int current = vertex * 3;
            double distance = planeDistance(input, current, plane);
            if ((distance >= 0) != (previousDistance >= 0)) {
                double fraction = previousDistance / (previousDistance - distance);
                for (int coordinate = 0; coordinate < 3; coordinate++) {
                    output[written * 3 + coordinate] = input[previous + coordinate]
                            + fraction * (input[current + coordinate] - input[previous + coordinate]);
                }
                written++;
            }
            if (distance >= 0) {
                System.arraycopy(input, current, output, written * 3, 3);
                written++;
            }
            previous = current;
            previousDistance = distance;
        }
        return written;
    }

    private static double planeDistance(double[] vertices, int offset, int plane) {
        double depth = vertices[offset];
        return switch (plane) {
            case 0 -> depth - EPSILON;
            case 1 -> depth + vertices[offset + 1];
            case 2 -> depth - vertices[offset + 1];
            case 3 -> depth + vertices[offset + 2];
            default -> depth - vertices[offset + 2];
        };
    }

    private void accept(Region section, double minU, double maxU, double minV, double maxV) {
        if (section.epoch == epoch) {
            if (minU >= section.minU - EPSILON && maxU <= section.maxU + EPSILON
                    && minV >= section.minV - EPSILON && maxV <= section.maxV + EPSILON) {
                return;
            }
            section.minU = Math.min(section.minU, minU);
            section.maxU = Math.max(section.maxU, maxU);
            section.minV = Math.min(section.minV, minV);
            section.maxV = Math.max(section.maxV, maxV);
        } else {
            section.epoch = epoch;
            section.minU = minU;
            section.maxU = maxU;
            section.minV = minV;
            section.maxV = maxV;
            section.queued = false;
        }
        if (!section.queued) {
            section.queued = true;
            queue.add(section);
        }
    }

    private void show(long key) {
        Section section = sections.get(key);
        if (section != null && !section.visible) {
            section.visible = true;
            newlyVisible.add(key);
        }
    }

    void drainRevealed(LongConsumer consumer) {
        var iterator = newlyVisible.iterator();
        while (iterator.hasNext()) {
            consumer.accept(iterator.nextLong());
        }
        newlyVisible.clear();
    }

    boolean isVisible(int x, int y, int z) {
        Section section = sections.get(key(x, y, z));
        return section == null || section.visible;
    }

    void markVisible(int x, int y, int z) {
        show(key(x, y, z));
    }

    void removeColumn(int x, int z) {
        originSection = null;
        cachedOrigin = null;
        var iterator = sections.keySet().iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            if (x(key) == x && z(key) == z) {
                iterator.remove();
                newlyVisible.remove(key);
                changed.remove(key);

            }
        }
    }

    void clear() {
        originSection = null;
        cachedOrigin = null;
        sections.clear();
        reached.clear();
        queue.clear();
        newlyVisible.clear();
        changed.clear();
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFL) << 42 | ((long) z & 0x3FFFFFL) << 20 | (long) y & 0xFFFFFL;
    }

    static int x(long key) {
        return (int) (key >> 42);
    }

    static int y(long key) {
        return (int) (key << 44 >> 44);
    }

    static int z(long key) {
        return (int) (key << 22 >> 42);
    }

    @FunctionalInterface
    interface OpenBlocks {
        boolean isOpen(int x, int y, int z);
    }

    private static final class Section {
        private final long[] open = new long[64];
        private Region[] regions;
        private boolean visible;
        private boolean merged;
        private boolean uniform;

        private boolean isOpen(int index) {
            return (open[index >> 6] & 1L << (index & 63)) != 0;
        }
    }

    private static final class Region {
        private final long sectionKey;
        private final int sample;
        private final long[] faces;
        private long epoch = Long.MIN_VALUE;
        private boolean queued;
        private double minU;
        private double maxU;
        private double minV;
        private double maxV;

        private Region(long sectionKey, int sample, long[] faces) {
            this.sectionKey = sectionKey;
            this.sample = sample;
            this.faces = faces;
        }
    }
}
