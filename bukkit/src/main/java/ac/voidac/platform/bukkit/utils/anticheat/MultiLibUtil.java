package ac.voidac.platform.bukkit.utils.anticheat;

import ac.voidac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class MultiLibUtil {

    private static final boolean IS_PRE_1_18 = PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_18);
    private static final MethodHandle EXTERNAL_PLAYER_HANDLE = createExternalPlayerHandle();

    private MultiLibUtil() {
    }

    private static MethodHandle createExternalPlayerHandle() {
        if (IS_PRE_1_18) return null;
        try {
            return MethodHandles.publicLookup().findVirtual(Player.class, "isExternalPlayer", MethodType.methodType(boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    public static boolean isExternalPlayer(Player player) {
        if (EXTERNAL_PLAYER_HANDLE == null) return false;
        try {
            return (boolean) EXTERNAL_PLAYER_HANDLE.invokeExact(player);
        } catch (Throwable e) {
            LogUtil.error("Failed to invoke external player method", e);
            return false;
        }
    }
}
