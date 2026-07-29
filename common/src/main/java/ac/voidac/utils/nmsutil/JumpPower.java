package ac.voidac.utils.nmsutil;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.math.VoidMath;
import ac.voidac.utils.math.Vector3dm;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

@UtilityClass
public class JumpPower {
    public static void jumpFromGround(@NotNull VoidPlayer player, @NotNull Vector3dm vector) {
        float jumpPower = getJumpPower(player);

        final OptionalInt jumpBoost = player.compensatedEntities.getPotionLevelForPlayer(PotionTypes.JUMP_BOOST);
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_14)) {
            // Pre-1.14 clients accumulate the boost in double, and know nothing about the
            // 1.20.5 zero-check or the 1.21.2 max(), so keep that path separate.
            double jumpVelocity = jumpPower;
            if (jumpBoost.isPresent()) {
                jumpVelocity += (jumpBoost.getAsInt() + 1) * 0.1F;
            }
            vector.setY(jumpVelocity);
        } else {
            if (jumpBoost.isPresent()) {
                jumpPower += 0.1f * (jumpBoost.getAsInt() + 1);
            }

            if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_5) && jumpPower <= 1.0E-5f)
                return;

            vector.setY(player.getClientVersion().isOlderThan(ClientVersion.V_1_21_2) ? jumpPower : Math.max(jumpPower, vector.getY()));
        }

        if (player.isSprinting) {
            float radRotation = VoidMath.radians(player.yaw);
            if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_5)) {
                vector.add(-player.trigHandler.sin(radRotation) * 0.2, 0.0, player.trigHandler.cos(radRotation) * 0.2);
            } else {
                vector.add(-player.trigHandler.sin(radRotation) * 0.2F, 0.0, player.trigHandler.cos(radRotation) * 0.2F);
            }
        }
    }

    public static float getJumpPower(@NotNull VoidPlayer player) {
        // JUMP_STRENGTH only became a player attribute in 1.20.5. Older clients jump with a
        // hardcoded 0.42, so reading the attribute for them desyncs every jump the moment
        // anything on the server touches that attribute.
        float jumpStrength = player.getClientVersion().isOlderThan(ClientVersion.V_1_20_5)
                ? 0.42F
                : (float) player.compensatedEntities.self.getAttributeValue(Attributes.JUMP_STRENGTH);
        return jumpStrength * getPlayerJumpFactor(player);
    }

    public static float getPlayerJumpFactor(@NotNull VoidPlayer player) {
        return BlockProperties.onHoneyBlock(player, player.mainSupportingBlockData, new Vector3d(player.lastX, player.lastY, player.lastZ)) ? 0.5f : 1f;
    }
}
