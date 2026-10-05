package ac.voidac.events.packets;

import ac.voidac.VoidAPI;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

public class PacketPlayerTick extends PacketListenerAbstract {

    public PacketPlayerTick() {
        super(PacketListenerPriority.NORMAL);
    }

    @Override
    public boolean isPreVia() {
        return true;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null || player.getClientVersion().isOlderThan(ClientVersion.V_1_21_2))
                return;

            PacketWorldBorder border = player.checkManager.getPacketCheck(PacketWorldBorder.class);
            border.tickBorder();
            player.storageEspDecoyManager.tickVisibility();
            player.antiEspManager.tick();
        } else if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2))
                return;

            PacketWorldBorder border = player.checkManager.getPacketCheck(PacketWorldBorder.class);
            border.tickBorder();
            player.storageEspDecoyManager.tickVisibility();
            if (!player.antiEspManager.isEnabled()) {
                player.antiEspManager.tick();
                return;
            }

            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            Vector3d position = flying.hasPositionChanged()
                    ? flying.getLocation().getPosition()
                    : new Vector3d(player.x, player.y, player.z);
            float yaw = flying.hasRotationChanged() ? flying.getLocation().getYaw() : player.yaw;
            float pitch = flying.hasRotationChanged() ? flying.getLocation().getPitch() : player.pitch;
            player.antiEspManager.tick(position.getX(), position.getY(), position.getZ(), yaw, pitch);
        }
    }

}
