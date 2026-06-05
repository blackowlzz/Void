package ac.voidac.platform.fabric.player;

import ac.voidac.platform.api.player.BlockTranslator;
import ac.voidac.platform.api.entity.VoidEntity;
import ac.voidac.platform.api.player.PlatformInventory;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.entity.AbstractFabricVoidEntity;
import ac.voidac.platform.fabric.utils.PolymerHook;
import ac.voidac.platform.fabric.utils.convert.FabricConversionUtil;
import ac.voidac.utils.common.arguments.CommonVoidArguments;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import lombok.Getter;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class AbstractFabricPlatformPlayer extends AbstractFabricVoidEntity implements PlatformPlayer {
    protected volatile ServerPlayer fabricPlayer;
    protected final AbstractFabricPlatformInventory inventory;
    private final @Nullable User user;
    @Getter private final BlockTranslator blockTranslator;

    public AbstractFabricPlatformPlayer(ServerPlayer player) {
        super(player);
        this.fabricPlayer = player;
        this.inventory = VoidFabricLoaderPlugin.LOADER.getPlatformPlayerFactory().getPlatformInventory(this);
        if (CommonVoidArguments.USE_CHAT_FAST_BYPASS.value()) {
            Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(fabricPlayer.getUUID());
            this.user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        } else {
            this.user = null;
        }

        this.blockTranslator = PolymerHook.createTranslator(this.fabricPlayer);
    }

    @Override
    public void kickPlayer(String textReason) {
        Component reason = LegacyComponentSerializer.legacySection().deserialize(textReason);
        fabricPlayer.connection.disconnect(VoidFabricLoaderPlugin.LOADER.getFabricConversionUtil().toNativeText(reason));
    }

    @Override
    public boolean isSneaking() {
        return fabricPlayer.isShiftKeyDown();
    }

    @Override
    public void setSneaking(boolean isSneaking) {
        fabricPlayer.setShiftKeyDown(isSneaking);
    }

    @Override
    public boolean hasPermission(String permission) {
        return getSender().hasPermission(permission);
    }

    @Override
    public boolean hasPermission(String permission, boolean defaultIfUnset) {
        return getSender().hasPermission(permission, defaultIfUnset);
    }

    @Override
    public void sendMessage(String message) {
        if (CommonVoidArguments.USE_CHAT_FAST_BYPASS.value() && user != null) {
            user.sendMessage(message);
        } else {
            fabricPlayer.displayClientMessage(VoidFabricLoaderPlugin.LOADER.getFabricMessageUtils().textLiteral(message), false);
        }
    }

    @Override
    public void sendMessage(Component message) {
        if (CommonVoidArguments.USE_CHAT_FAST_BYPASS.value() && user != null) {
            user.sendMessage(message);
        } else {
            fabricPlayer.displayClientMessage(VoidFabricLoaderPlugin.LOADER.getFabricConversionUtil().toNativeText(message), false);
        }
    }

    @Override
    public boolean isOnline() {
        return !fabricPlayer.hasDisconnected();
    }

    @Override
    public String getName() {
        return fabricPlayer.getName().getString();
    }

    @Override
    public void updateInventory() {
        fabricPlayer.containerMenu.broadcastChanges();
    }

    @Override
    public Vector3d getPosition() {
        return new Vector3d(fabricPlayer.getX(), fabricPlayer.getY(), fabricPlayer.getZ());
    }

    @Override
    public PlatformInventory getInventory() {
        return inventory;
    }

    @Override
    public VoidEntity getVehicle() {
        Entity vehicle = fabricPlayer.getVehicle();
        return vehicle != null ? VoidFabricLoaderPlugin.LOADER.getPlatformPlayerFactory().getPlatformEntity(vehicle) : null;
    }

    @Override
    public GameMode getGameMode() {
        return FabricConversionUtil.fromFabricGameMode(fabricPlayer.gameMode.getGameModeForPlayer());
    }

    @Override
    public void setGameMode(GameMode gameMode) {
        fabricPlayer.setGameMode(FabricConversionUtil.toFabricGameMode(gameMode));
    }

    @Override
    public UUID getUniqueId() {
        return fabricPlayer.getUUID();
    }

    @Override
    public boolean isExternalPlayer() {
        return false;
    }

    @Override
    public void sendPluginMessage(String channelName, byte[] byteArray) {
        // You might want to use Fabric's networking system here
//        CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(
//                Identifier.of(channelName),
//                new PacketByteBuf(Unpooled.wrappedBuffer(byteArray))
//        );
//        fabricPlayer.networkHandler.sendPacket(packet);
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceNativePlayer(Object nativePlayerObject) {
        this.fabricPlayer = (ServerPlayer) nativePlayerObject;
    }

    @Override
    public @NotNull ServerPlayer getNative() {
        return this.fabricPlayer;
    }

    @Override
    public boolean isDead() {
        return fabricPlayer.isDeadOrDying();
    }
}
