package ac.voidac.utils.data.interpolation;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.voidac.utils.data.interpolation.stepped.SteppedEntityInterpolation;
import ac.voidac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class EntityInterpolations {

    public static EntityInterpolation create(VoidPlayer player, PacketEntity entity, SimpleCollisionBox position) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_26_3) && usesStepped(entity.getType())) {
            return new SteppedEntityInterpolation(updateInterval(entity.getType()), position);
        }

        return new LegacyEntityInterpolation(player, entity, position);
    }

    public static boolean usesStepped(EntityType type) {
        return EntityTypes.isTypeInstanceOf(type, EntityTypes.LIVINGENTITY) && type != EntityTypes.SHULKER;
    }

    static int updateInterval(EntityType type) {
        if (type == EntityTypes.PLAYER || type == EntityTypes.ALLAY || type == EntityTypes.MANNEQUIN) {
            return 2;
        }

        return 3;
    }

}
