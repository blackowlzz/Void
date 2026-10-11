package ac.voidac.platform.fabric.utils.world;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public final class LevelChunkUtil {
    private LevelChunkUtil() {}

    public static boolean hasChunkAt(Level level, int chunkX, int chunkZ) {
        return ((LevelAccessor) level).getChunkSource().hasChunk(chunkX, chunkZ);
    }
}
