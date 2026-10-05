package ac.voidac.utils.latency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiEspPolicyTest {

    @Test
    void masksOnlyWhenViewerIsAboveThreshold() {
        assertFalse(AntiEspPolicy.shouldMaskUnderground(0.0, 0));
        assertFalse(AntiEspPolicy.shouldMaskUnderground(0.0664, 0));
        assertFalse(AntiEspPolicy.shouldMaskUnderground(0.9999, 0));
        assertTrue(AntiEspPolicy.shouldMaskUnderground(1.0, 0));
        assertTrue(AntiEspPolicy.shouldMaskUnderground(20.0, 0));
        assertFalse(AntiEspPolicy.shouldMaskUnderground(-0.01, 0));
    }

    @Test
    void keepsUndergroundStateStableWhileJumpingAtThreshold() {
        assertTrue(AntiEspPolicy.resolveUndergroundState(false, 0.0664, 0));
        assertTrue(AntiEspPolicy.resolveUndergroundState(true, 1.25, 0));
        assertTrue(AntiEspPolicy.resolveUndergroundState(true, 1.9999, 0));
        assertFalse(AntiEspPolicy.resolveUndergroundState(true, 2.0, 0));
        assertFalse(AntiEspPolicy.resolveUndergroundState(false, 1.25, 0));
    }

    @Test
    void limitsRealChunksToTheUndergroundRevealRadius() {
        assertFalse(AntiEspPolicy.shouldMaskChunk(false, true, 10, 10, 13, 10, 2));
        assertFalse(AntiEspPolicy.shouldMaskChunk(true, true, 10, 10, 12, 8, 2));
        assertTrue(AntiEspPolicy.shouldMaskChunk(true, true, 10, 10, 13, 10, 2));
        assertTrue(AntiEspPolicy.shouldMaskChunk(true, false, 10, 10, 10, 10, 2));
    }

    @Test
    void revealsPovSectionsBeyondTheUndergroundChunkRadius() {
        assertTrue(AntiEspPolicy.shouldRevealSection(true, false));
        assertTrue(AntiEspPolicy.shouldRevealSection(false, true));
        assertFalse(AntiEspPolicy.shouldRevealSection(false, false));
    }

    @Test
    void calculatesMaskedRowsAcrossNegativeWorldHeight() {
        assertEquals(16, AntiEspPolicy.maskedRows(-64, 0, 0));
        assertEquals(16, AntiEspPolicy.maskedRows(-64, 3, 0));
        assertEquals(0, AntiEspPolicy.maskedRows(-64, 4, 0));
        assertEquals(8, AntiEspPolicy.maskedRows(-64, 4, 8));
        assertEquals(0, AntiEspPolicy.maskedRows(0, 0, 0));
    }

    @Test
    void undergroundEntitiesRequireLineOfSight() {
        assertTrue(AntiEspPolicy.shouldHideEntity(0, -10, 0, 4, -10, 0, 0, 8, 96, false));
        assertFalse(AntiEspPolicy.shouldHideEntity(0, 10, 0, 40, -10, 0, 0, 8, 96, true));
        assertTrue(AntiEspPolicy.shouldHideEntity(0, 10, 0, 40, -10, 0, 0, 8, 96, false));
        assertTrue(AntiEspPolicy.shouldHideEntity(0, 10, 0, 100, -10, 0, 0, 8, 96, true));
        assertFalse(AntiEspPolicy.shouldHideEntity(0, 10, 0, 40, 1, 0, 0, 8, 96, false));
    }

    @Test
    void detectsThresholdAndChunkTransitionsForRealDataRefresh() {
        assertTrue(AntiEspPolicy.needsUndergroundRefresh(1, 0, 0, 0, 0, 0, 0));
        assertTrue(AntiEspPolicy.needsUndergroundRefresh(0, 1, 0, 0, 0, 0, 0));
        assertTrue(AntiEspPolicy.needsUndergroundRefresh(-1, -1, 0, 0, 0, 1, 0));
        assertFalse(AntiEspPolicy.needsUndergroundRefresh(1, 1, 0, 0, 0, 1, 0));
        assertFalse(AntiEspPolicy.needsUndergroundRefresh(-1, -1, 0, 0, 0, 0, 0));
    }
}
