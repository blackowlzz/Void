package ac.voidac.platform.fabric.utils.convert;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import net.kyori.adventure.text.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class FabricConversionUtil implements IFabricConversionUtil {
    private IFabricConversionUtil fabricConversionUtilSupplier;

    private final Function<net.minecraft.world.item.ItemStack, ItemStack> itemStackMapperFunction = (fabricStack) -> {
        throw new UnsupportedOperationException();
    };
    private final Function<Component, net.minecraft.network.chat.Component> nativeTextMapperFunction = (component) -> {
        throw new UnsupportedOperationException();
    };

    public ItemStack fromFabricItemStack(net.minecraft.world.item.ItemStack fabricStack) {
        return fabricConversionUtilSupplier.fromFabricItemStack(fabricStack);
    }

    public net.minecraft.network.chat.Component toNativeText(Component component) {
        return fabricConversionUtilSupplier.toNativeText(component);
    }

    public static GameType toFabricGameMode(GameMode gameMode) {
        return switch (gameMode) {
            case CREATIVE -> GameType.CREATIVE;
            case SURVIVAL -> GameType.SURVIVAL;
            case ADVENTURE -> GameType.ADVENTURE;
            case SPECTATOR -> GameType.SPECTATOR;
        };
    }

    public static GameMode fromFabricGameMode(GameType fabricGameMode) {
        return switch (fabricGameMode) {
            case CREATIVE -> GameMode.CREATIVE;
            case SURVIVAL -> GameMode.SURVIVAL;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
            default -> throw new IllegalArgumentException("Unknown Fabric GameMode: " + fabricGameMode);
        };
    }

    @Contract(value = "null -> null; !null -> !null", pure = true)
    public static @Nullable InteractionHand fromFabricHand(@Nullable net.minecraft.world.InteractionHand hand) {
        return hand == null ? null : switch (hand) {
            case OFF_HAND -> InteractionHand.OFF_HAND;
            case MAIN_HAND -> InteractionHand.MAIN_HAND;
        };
    }

    public static BlockFace fromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST  -> BlockFace.WEST;
            case EAST  -> BlockFace.EAST;
            case UP    -> BlockFace.UP;
            case DOWN  -> BlockFace.DOWN;
        };
    }
}
