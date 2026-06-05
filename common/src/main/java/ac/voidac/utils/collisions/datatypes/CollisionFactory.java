package ac.voidac.utils.collisions.datatypes;

import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;

public interface CollisionFactory {
    CollisionBox fetch(VoidPlayer player, ClientVersion version, WrappedBlockState block, int x, int y, int z);
}
