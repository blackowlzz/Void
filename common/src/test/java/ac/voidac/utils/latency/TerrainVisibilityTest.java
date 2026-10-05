package ac.voidac.utils.latency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainVisibilityTest {

    @Test
    void revealsTheStraightTunnelWithoutFollowingTheHiddenTurn() {
        TerrainVisibility visibility = enclosedWorld();
        for (int x = 0; x < 8; x++) {
            visibility.put(x, -3, 0, (localX, localY, localZ) -> localY == 8 && localZ == 8);
        }
        visibility.put(8, -3, 0, (x, y, z) -> y == 8 && (z == 8 || x == 8));
        for (int z = 1; z <= 8; z++) {
            visibility.put(8, -3, z, (localX, localY, localZ) -> localY == 8 && localX == 8);
        }

        visibility.reveal(0, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(8, -3, 0));
        assertFalse(visibility.isVisible(8, -3, 8));
        assertFalse(visibility.isVisible(5, -3, 5));
    }

    @Test
    void doesNotPropagateThroughSolidShellIntoASeparateRoom() {
        TerrainVisibility visibility = enclosedWorld();
        visibility.put(0, -3, 0, (x, y, z) -> true);
        visibility.put(2, -3, 0, (x, y, z) -> true);
        visibility.put(3, -3, 0, (x, y, z) -> true);

        visibility.reveal(0, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(1, -3, 0));
        assertFalse(visibility.isVisible(2, -3, 0));
        assertFalse(visibility.isVisible(3, -3, 0));
    }

    @Test
    void requiresMatchingOpenBlocksAcrossASectionFace() {
        TerrainVisibility visibility = enclosedWorld();
        for (int sectionX = 0; sectionX < 2; sectionX++) {
            visibility.put(sectionX, -3, 0, (x, y, z) -> y == 8 && z == 8);
        }
        for (int sectionX = 2; sectionX <= 5; sectionX++) {
            visibility.put(sectionX, -3, 0, (x, y, z) -> y == 9 && z == 9);
        }

        visibility.reveal(0, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(2, -3, 0));
        assertFalse(visibility.isVisible(3, -3, 0));
        assertFalse(visibility.isVisible(5, -3, 0));
    }

    @Test
    void revealsACaveConnectedToTheSurfaceBeforeThePlayerCrossesTheThreshold() {
        TerrainVisibility visibility = enclosedWorld();
        for (int sectionY = -3; sectionY <= 0; sectionY++) {
            visibility.put(6, sectionY, 0, (x, y, z) -> x == 8 && z == 8);
        }
        visibility.put(6, -3, 0, (x, y, z) -> z == 8 && (x == 8 || y == 8));
        visibility.put(7, -3, 0, (x, y, z) -> y == 8 && z == 8);

        visibility.reveal(6, 0, 0, 0, 0);

        assertTrue(visibility.isVisible(7, -3, 0));
    }

    @Test
    void includesASectionIntersectedByAnUnalignedThreshold() {
        TerrainVisibility visibility = enclosedWorld();
        visibility.put(6, -1, 0, (x, y, z) -> x == 8 && z == 8);
        visibility.put(6, -2, 0, (x, y, z) -> x == 8 && z == 8);
        visibility.put(6, -3, 0, (x, y, z) -> x == 8 && z == 8);

        visibility.reveal(0, 1, 0, 0, -8);

        assertTrue(visibility.isVisible(6, -1, 0));
        assertFalse(visibility.isVisible(6, -3, 0));
    }

    @Test
    void jumpingAtTheThresholdDoesNotRemaskAnAlreadyRevealedCave() {
        TerrainVisibility visibility = enclosedWorld();
        for (int sectionX = 0; sectionX <= 7; sectionX++) {
            visibility.put(sectionX, -2, 0, (x, y, z) -> y == 8 && z == 8);
        }

        visibility.reveal(0, -2, 0, 0, 0);
        assertTrue(visibility.isVisible(7, -2, 0));

        visibility.reveal(0, 0, 0, 0, 0);
        visibility.reveal(0, -1, 0, 0, 0);
        visibility.reveal(0, 0, 0, 0, 0);

        assertTrue(visibility.isVisible(7, -2, 0));
    }

    @Test
    void openingAWallRevealsTheCaveBeyondIt() {
        TerrainVisibility visibility = enclosedWorld();
        visibility.put(0, -3, 0, (x, y, z) -> y == 8 && z == 8);
        for (int sectionX = 2; sectionX <= 7; sectionX++) {
            visibility.put(sectionX, -3, 0, (x, y, z) -> y == 8 && z == 8);
        }
        visibility.reveal(0, -3, 0, 0, 0);
        assertFalse(visibility.isVisible(7, -3, 0));

        visibility.put(1, -3, 0, (x, y, z) -> y == 8 && z == 8);
        visibility.reveal(0, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(7, -3, 0));

        visibility.put(1, -3, 0, (x, y, z) -> false);
        visibility.reveal(0, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(7, -3, 0));
    }

    @Test
    void doesNotUseUnknownSectionsToRevealDistantCaves() {
        TerrainVisibility visibility = enclosedWorld();
        for (int sectionX = 2; sectionX <= 7; sectionX++) {
            visibility.put(sectionX, -3, 0, (x, y, z) -> y == 8 && z == 8);
        }
        visibility.removeColumn(1, 0);

        visibility.reveal(0, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(1, -3, 0));
        assertFalse(visibility.isVisible(7, -3, 0));
        assertTrue(visibility.isVisible(100, -3, 100));
    }

    @Test
    void unloadingAColumnDropsItsVisibilityBeforeItIsLoadedAgain() {
        TerrainVisibility visibility = enclosedWorld();
        visibility.reveal(0, -3, 0, 0, 0);
        assertTrue(visibility.isVisible(0, -3, 0));

        visibility.removeColumn(0, 0);
        assertTrue(visibility.isVisible(0, -3, 0));

        visibility.put(0, -3, 0, (x, y, z) -> false);

        assertFalse(visibility.isVisible(0, -3, 0));
    }

    @Test
    void clearsVisibilityAndTopologyOnWorldChange() {
        TerrainVisibility visibility = enclosedWorld();
        visibility.reveal(0, -3, 0, 0, 0);

        visibility.clear();
        assertTrue(visibility.isVisible(0, -3, 0));
        visibility.put(0, -3, 0, (x, y, z) -> false);

        assertFalse(visibility.isVisible(0, -3, 0));
    }

    @Test
    void preservesTheCaveShellWhenADiagonalColumnArrivesLater() {
        TerrainVisibility visibility = enclosedWorld();
        visibility.removeColumn(1, 1);
        visibility.reveal(0, -3, 0, 0, 0);
        visibility.put(1, -2, 1, (x, y, z) -> false);

        visibility.reveal(0, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(1, -2, 1));
    }

    @Test
    void surfacePlayerDoesNotRevealDeepCavesThroughStoneOrMissingNeighbors() {
        TerrainVisibility visibility = new TerrainVisibility();
        for (int x = -14; x <= -10; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -7; z <= -3; z++) {
                    boolean open = y < 0 || y == 4;
                    visibility.put(x, y, z, (localX, localY, localZ) -> open);
                }
            }
        }

        visibility.revealFrom(-185, 70.62, -80, 2, 0);

        assertFalse(visibility.isVisible(-12, -3, -5));
        assertFalse(visibility.isVisible(-10, -3, -3));
    }

    @Test
    void retainsOpenSightlinesInEveryDirectionAndAtSectionBoundaries() {
        for (int axis = 0; axis < 3; axis++) {
            for (int sign : new int[]{-1, 1}) {
                TerrainVisibility visibility = new TerrainVisibility();
                for (int distance = 0; distance <= 7; distance++) {
                    int x = axis == 0 ? distance * sign : 0;
                    int y = axis == 1 ? distance * sign - 10 : -10;
                    int z = axis == 2 ? distance * sign : 0;
                    visibility.put(x, y, z, (localX, localY, localZ) -> true);
                }

                visibility.revealFrom(0, -160, 0, 0, 0);

                assertTrue(visibility.isVisible(axis == 0 ? 7 * sign : 0,
                        axis == 1 ? 7 * sign - 10 : -10, axis == 2 ? 7 * sign : 0));
            }
        }
    }

    @Test
    void distantSurfaceOpeningDoesNotRevealACaveBelowThePlayer() {
        TerrainVisibility visibility = enclosedWorld();
        visibility.put(0, 0, 0, (x, y, z) -> true);
        for (int y = -3; y <= 1; y++) {
            visibility.put(8, y, 0, (x, localY, z) -> x == 8 && z == 8);
        }
        for (int x = 0; x <= 8; x++) {
            visibility.put(x, -3, 0, (localX, y, z) -> y == 8 && z == 8);
        }

        visibility.reveal(0, 0, 0, 0, 0);

        assertFalse(visibility.isVisible(0, -3, 0));
        assertFalse(visibility.isVisible(8, -3, 0));
    }

    @Test
    void movingToTheCornerRevealsTheNextStraightPassage() {
        TerrainVisibility visibility = enclosedWorld();
        for (int x = 0; x < 8; x++) {
            visibility.put(x, -3, 0, (localX, y, z) -> y == 8 && z == 8);
        }
        visibility.put(8, -3, 0, (x, y, z) -> y == 8 && (x == 8 || z == 8));
        for (int z = 1; z <= 8; z++) {
            visibility.put(8, -3, z, (x, y, localZ) -> x == 8 && y == 8);
        }
        visibility.reveal(0, -3, 0, 0, 0);
        assertFalse(visibility.isVisible(8, -3, 8));

        visibility.reveal(8, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(8, -3, 8));
    }

    @Test
    void aThinFloorInsideTheEyeSectionDoesNotExposeTheCaveBelow() {
        TerrainVisibility visibility = new TerrainVisibility();
        for (int y = -4; y <= 4; y++) {
            int sectionY = y;
            visibility.put(-12, y, -5, (x, localY, z) -> sectionY != 4 || localY != 4);
        }

        visibility.revealFrom(-185, 70.62, -79.5, 0, 0);

        assertFalse(visibility.isVisible(-12, -3, -5));
    }

    @Test
    void disconnectedAirPocketsInTheSameSectionDoNotCreateASightline() {
        TerrainVisibility visibility = enclosedWorld();
        for (int x = 0; x <= 7; x++) {
            int sectionX = x;
            visibility.put(x, -3, 0, (localX, y, z) -> y == 8 && z == 8 && (sectionX != 3 || localX != 8));
        }

        visibility.reveal(0, -3, 0, 0, 0);

        assertTrue(visibility.isVisible(3, -3, 0));
        assertFalse(visibility.isVisible(7, -3, 0));
    }

    private static TerrainVisibility enclosedWorld() {
        TerrainVisibility visibility = new TerrainVisibility();
        for (int x = -3; x <= 11; x++) {
            for (int y = -5; y <= 1; y++) {
                for (int z = -3; z <= 11; z++) {
                    visibility.put(x, y, z, (localX, localY, localZ) -> false);
                }
            }
        }
        return visibility;
    }
}
