package ac.voidac.utils.latency;

import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_8.Chunk_v1_8;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AntiEspTerrainSnapshotTest {
    @Test
    void retainsTheServerSnapshotWhenThePredictionWorldChanges() {
        Chunk_v1_8 source = new Chunk_v1_8(false);
        source.set(3, 4, 5, 16);
        AntiEspTerrain.Section snapshot = new AntiEspTerrain.Section(source);

        source.set(3, 4, 5, 0);

        assertEquals(16, snapshot.get(3, 4, 5));
    }

    @Test
    void restoresTheLatestBlockUpdateInsteadOfTheOriginalChunk() {
        Chunk_v1_8 source = new Chunk_v1_8(false);
        source.set(3, 4, 5, 16);
        AntiEspTerrain.Section snapshot = new AntiEspTerrain.Section(source);
        snapshot.maskedRows = 16;

        snapshot.update(3, 4, 5, 32);
        var changes = snapshot.createReveal(-4, -2, 7, 0);

        assertEquals(1, changes.length);
        assertEquals(32, changes[0].getBlockId());
        assertEquals(-61, changes[0].getX());
        assertEquals(-28, changes[0].getY());
        assertEquals(117, changes[0].getZ());
        assertEquals(16, source.getBlockId(3, 4, 5));
    }

    @Test
    void restoresAirWhenAMaskedBlockHasBeenBroken() {
        Chunk_v1_8 source = new Chunk_v1_8(false);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    source.set(x, y, z, 16);
                }
            }
        }
        AntiEspTerrain.Section snapshot = new AntiEspTerrain.Section(source);
        snapshot.maskedRows = 16;
        snapshot.update(8, 8, 8, 0);

        var changes = snapshot.createReveal(0, -1, 0, 16);

        assertEquals(1, changes.length);
        assertEquals(0, changes[0].getBlockId());
        assertEquals(-8, changes[0].getY());
    }

    @Test
    void doesNotOverwriteRowsThatWereNeverMasked() {
        Chunk_v1_8 source = new Chunk_v1_8(false);
        source.set(2, 3, 4, 16);
        source.set(2, 10, 4, 32);
        AntiEspTerrain.Section snapshot = new AntiEspTerrain.Section(source);
        snapshot.maskedRows = 8;

        var changes = snapshot.createReveal(0, -1, 0, 0);

        assertEquals(1, changes.length);
        assertEquals(16, changes[0].getBlockId());
    }
}
