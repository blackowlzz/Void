package ac.voidac.utils.latency;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class AntiEspBlockEntityDataTest {
    @Test
    void absentChunkTagBecomesACompoundForTheStandalonePacket() {
        NBTCompound first = AntiEspTerrain.copyBlockEntityTag(null);
        NBTCompound second = AntiEspTerrain.copyBlockEntityTag(null);
        assertNotNull(first);
        assertEquals(new NBTCompound(), first);
        assertNotSame(first, second);
    }

    @Test
    void preservesDataAndIsolatesNestedTagsFromLaterChanges() {
        NBTCompound original = new NBTCompound();
        NBTCompound nested = new NBTCompound();
        nested.setTag("text", new NBTString("original"));
        original.setTag("front_text", nested);
        NBTCompound snapshot = AntiEspTerrain.copyBlockEntityTag(original);
        NBTCompound outbound = AntiEspTerrain.copyBlockEntityTag(snapshot);
        assertEquals(original, snapshot);
        nested.setTag("text", new NBTString("changed"));
        assertEquals(outbound, snapshot);
        assertNotSame(original, snapshot);
        assertNotSame(snapshot, outbound);
    }
}
