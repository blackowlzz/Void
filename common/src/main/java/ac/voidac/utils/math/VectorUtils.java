package ac.voidac.utils.math;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.collisions.datatypes.SimpleCollisionBox;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class VectorUtils {
    // Pre-1.17 vanilla clients compute this length in single precision (float), not double.
    // Skipping the cast produces a tiny mismatch on essentially every diagonal movement input.
    public static double getVanillaLength(ClientVersion version, Vector3dm vec) {
        double lengthSquared = vec.getX() * vec.getX() + vec.getY() * vec.getY() + vec.getZ() * vec.getZ();
        return version.isOlderThan(ClientVersion.V_1_17) ? (float) Math.sqrt(lengthSquared) : Math.sqrt(lengthSquared);
    }

    /**
     * Vanilla-accurate vector normalization.
     * <p>
     * Unlike {@link Vector3dm#normalize()} this collapses to a zero vector below the
     * client's epsilon instead of dividing through and blowing a near-zero vector up
     * to full length. Vanilla drops those pushes entirely; scaling them up instead
     * hands the player movement the client never applied, which shows up as offset.
     */
    public static @NotNull Vector3dm normalize(@NotNull VoidPlayer player, @NotNull Vector3dm vec) {
        return normalize(player.getClientVersion(), vec);
    }

    public static @NotNull Vector3dm normalize(ClientVersion version, @NotNull Vector3dm vec) {
        double length = getVanillaLength(version, vec);
        // 1.21.2 tightened the cutoff, and did it in float
        double epsilon = version.isNewerThanOrEquals(ClientVersion.V_1_21_2) ? 1.0E-5F : 1.0E-4D;
        return length < epsilon
                ? new Vector3dm()
                : new Vector3dm(vec.getX() / length, vec.getY() / length, vec.getZ() / length);
    }

    public static @NotNull Vector3dm cutBoxToVector(@NotNull Vector3dm vectorToCutTo, @NotNull Vector3dm min, @NotNull Vector3dm max) {
        SimpleCollisionBox box = new SimpleCollisionBox(min, max).sort();
        return cutBoxToVector(vectorToCutTo, box);
    }

    @Contract("_, _ -> new")
    public static @NotNull Vector3dm cutBoxToVector(@NotNull Vector3dm vectorCutTo, @NotNull SimpleCollisionBox box) {
        return cutBoxToVector(vectorCutTo.getX(), vectorCutTo.getY(), vectorCutTo.getZ(), box);
    }

    public static @NotNull Vector3dm cutBoxToVector(double x, double y, double z, @NotNull SimpleCollisionBox box) {
        return new Vector3dm(VoidMath.clamp(x, box.minX, box.maxX),
                VoidMath.clamp(y, box.minY, box.maxY),
                VoidMath.clamp(z, box.minZ, box.maxZ));
    }

    @Contract("_ -> new")
    public static @NotNull Vector3dm fromVec3d(@NotNull Vector3d vector3d) {
        return new Vector3dm(vector3d.getX(), vector3d.getY(), vector3d.getZ());
    }

    // Clamping stops the player from causing an integer overflow and crashing the netty thread
    @Contract("_ -> new")
    public static @NotNull Vector3d clampVector(@NotNull Vector3d toClamp) {
        double x = VoidMath.clamp(toClamp.getX(), -3.0E7D, 3.0E7D);
        double y = VoidMath.clamp(toClamp.getY(), -2.0E7D, 2.0E7D);
        double z = VoidMath.clamp(toClamp.getZ(), -3.0E7D, 3.0E7D);

        return new Vector3d(x, y, z);
    }
}
