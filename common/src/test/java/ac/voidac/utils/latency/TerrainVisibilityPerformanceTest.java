package ac.voidac.utils.latency;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainVisibilityPerformanceTest {
    @Test
    void repeatedMovementMeasurement() {
        TerrainVisibility visibility = new TerrainVisibility();
        visibility.put(0, -3, 0, (x, y, z) -> y != 0);
        move(visibility, 2000);
        long[] samples = new long[9];
        for (int sample = 0; sample < samples.length; sample++) {
            long start = System.nanoTime();
            move(visibility, 2000);
            samples[sample] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        System.out.println("terrain-2000-moves median-ns=" + samples[4]
                + " min-ns=" + samples[0] + " max-ns=" + samples[8]);
        assertEquals(true, visibility.isVisible(0, -3, 0));
    }

    @Test
    void matchesFreshOriginSearchAcrossMovementEditsAndWorldChanges() {
        TerrainVisibility cached = new TerrainVisibility();
        TerrainVisibility fresh = new TerrainVisibility();
        for (int phase = 0; phase < 4; phase++) {
            if (phase == 2) {
                cached.clear();
                fresh.clear();
            }
            if (phase == 3) {
                cached.removeColumn(0, 0);
                fresh.removeColumn(0, 0);
            }
            boolean divider = phase % 2 == 0;
            TerrainVisibility.OpenBlocks origin = (x, y, z) -> y == 8 && (!divider || x != 8);
            for (int x = -4; x <= 4; x++) {
                cached.put(x, -3, 0, x == 0 ? origin : (a, b, c) -> b == 8);
                fresh.put(x, -3, 0, x == 0 ? origin : (a, b, c) -> b == 8);
            }
            for (int step = 0; step < 32; step++) {
                fresh.put(0, -3, 0, origin);
                double x = (step * 7 % 16) + 0.5;
                double z = (step * 3 % 16) + 0.5;
                cached.revealFrom(x, -39.5, z, 0, 0);
                fresh.revealFrom(x, -39.5, z, 0, 0);
                Set<Long> cachedChanges = new HashSet<>();
                Set<Long> freshChanges = new HashSet<>();
                cached.drainRevealed(cachedChanges::add);
                fresh.drainRevealed(freshChanges::add);
                assertEquals(freshChanges, cachedChanges, "phase=" + phase + " step=" + step);
                for (int column = -4; column <= 4; column++) {
                    assertEquals(fresh.isVisible(column, -3, 0), cached.isVisible(column, -3, 0));
                }
            }
        }
    }

    private void move(TerrainVisibility visibility, int count) {
        for (int step = 0; step < count; step++) {
            visibility.revealFrom((step % 14) + 0.5, -39.5, (step * 3 % 14) + 0.5, 0, 0);
        }
    }
}
