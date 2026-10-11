package ac.voidac.platform.bukkit.player;

import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.reflection.ReflectionUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class SharedFlagsResync {
    private static final @Nullable SharedFlagsResync INSTANCE = create();

    private final Method getHandle;
    private final Method getDataWatcher;
    private final Method markDirty;
    private final Object sharedFlagsId;

    private SharedFlagsResync(Method getHandle, Method getDataWatcher, Method markDirty, Object sharedFlagsId) {
        this.getHandle = getHandle;
        this.getDataWatcher = getDataWatcher;
        this.markDirty = markDirty;
        this.sharedFlagsId = sharedFlagsId;
    }

    static void resync(@NotNull Player player) {
        if (INSTANCE != null) {
            try {
                INSTANCE.markDirty.invoke(INSTANCE.getDataWatcher.invoke(INSTANCE.getHandle.invoke(player)), INSTANCE.sharedFlagsId);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                LogUtil.warn("Failed to resync shared entity flags for " + player.getName() + ", toggling sneaking instead: " + e);
            }
        }

        player.setSneaking(!player.isSneaking());
    }

    private static @Nullable SharedFlagsResync create() {
        final ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();

        try {
            final String nmsPackage;
            Class<?> craftEntity = ReflectionUtils.getClass("org.bukkit.craftbukkit.entity.CraftEntity");
            if (craftEntity != null) {
                nmsPackage = null;
            } else {
                nmsPackage = Bukkit.getServer().getClass().getPackageName().split("\\.")[3];
                craftEntity = Class.forName("org.bukkit.craftbukkit." + nmsPackage + ".entity.CraftEntity");
            }

            final Method getHandle = craftEntity.getMethod("getHandle");
            final Class<?> entity = getHandle.getReturnType();

            final Method getDataWatcher = entity.getMethod(nmsPackage == null ? "getEntityData" : switch (nmsPackage) {
                case "v1_8_R3", "v1_9_R1", "v1_9_R2", "v1_10_R1",
                     "v1_11_R1", "v1_12_R1", "v1_13_R1", "v1_13_R2",
                     "v1_14_R1", "v1_15_R1", "v1_16_R1", "v1_16_R2",
                     "v1_16_R3", "v1_17_R1" -> "getDataWatcher";
                case "v1_18_R1", "v1_18_R2", "v1_19_R1" -> "ai";
                case "v1_19_R2", "v1_20_R2" -> "al";
                case "v1_19_R3", "v1_20_R1" -> "aj";
                case "v1_20_R3" -> "an";
                case "v1_20_R4" -> "ap";
                case "v1_21_R1", "v1_21_R4" -> "ar";
                case "v1_21_R2", "v1_21_R3", "v1_21_R5" -> "au";
                case "v1_21_R6" -> "aC";
                case "v1_21_R7" -> "aD";
                default -> throw new IllegalStateException("unsupported server version " + nmsPackage + "/" + version.getReleaseName());
            });

            final Class<?> dataWatcher = getDataWatcher.getReturnType();

            if (version.isOlderThan(ServerVersion.V_1_9)) {
                return new SharedFlagsResync(getHandle, getDataWatcher, dataWatcher.getMethod("update", int.class), 0);
            }

            final Field sharedFlags = entity.getDeclaredField(nmsPackage == null ? "DATA_SHARED_FLAGS_ID" : switch (nmsPackage) {
                case "v1_9_R1" -> "ax";
                case "v1_9_R2" -> "ay";
                case "v1_10_R1", "v1_18_R1" -> "aa";
                case "v1_11_R1", "v1_12_R1", "v1_17_R1",
                     "v1_18_R2", "v1_19_R1", "v1_19_R2" -> "Z";
                case "v1_13_R1", "v1_13_R2" -> "ac";
                case "v1_14_R1" -> "W";
                case "v1_15_R1", "v1_16_R1" -> "T";
                case "v1_16_R2", "v1_16_R3" -> "S";
                case "v1_19_R3", "v1_20_R1" -> "an";
                case "v1_20_R2", "v1_20_R3" -> "ao";
                case "v1_20_R4", "v1_21_R1" -> "ap";
                case "v1_21_R2", "v1_21_R3", "v1_21_R4" -> "am";
                case "v1_21_R5" -> "az";
                case "v1_21_R6", "v1_21_R7" -> "aA";
                default -> throw new IllegalStateException("unsupported server version " + nmsPackage + "/" + version.getReleaseName());
            });
            sharedFlags.setAccessible(true);

            return new SharedFlagsResync(getHandle, getDataWatcher, dataWatcher.getMethod("markDirty", sharedFlags.getType()), sharedFlags.get(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LogUtil.warn("Shared entity flag resync is unavailable on this server, elytra resyncs will toggle sneaking instead: " + e);
            return null;
        }
    }
}
