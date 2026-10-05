package ac.voidac.utils.latency;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedCaveSearchTest {

    @Test
    void discoversAConnectedTunnelAcrossSectionBoundaries() {
        LongOpenHashSet sections = search((x, y, z) -> y == 0 && z == 0 && x >= 0 && x <= 24);

        assertTrue(sections.contains(ConnectedCaveSearch.sectionKey(0, 0, 0)));
        assertTrue(sections.contains(ConnectedCaveSearch.sectionKey(1, 0, 0)));
    }

    @Test
    void doesNotRevealTerrainBehindAClosedWall() {
        LongOpenHashSet sections = search((x, y, z) -> y == 0 && z == 0 && x >= 0 && x <= 24 && x != 10);

        assertTrue(sections.contains(ConnectedCaveSearch.sectionKey(0, 0, 0)));
        assertFalse(sections.contains(ConnectedCaveSearch.sectionKey(1, 0, 0)));
    }

    private static LongOpenHashSet search(ConnectedCaveSearch.Passability passability) {
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet sections = new LongOpenHashSet();
        ConnectedCaveSearch.findVisibleSections(
                0, 0, 0, 32, -4, 4, 4096,
                passability, visited, queue, sections
        );
        return sections;
    }
}
