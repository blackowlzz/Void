package ac.voidac.checks.impl.badpackets;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "BadPacketsT", stableKey = "void.badpackets.invalid_interact_vector")
public class BadPacketsT extends Check implements PacketCheck {
    private final boolean exempt = player.getClientVersion().isOlderThan(ClientVersion.V_1_8);

    private final double maxHorizontalDisplacement;
    private final double minVerticalDisplacement;
    private final double maxVerticalDisplacement;

    public BadPacketsT(final VoidPlayer player) {
        super(player);
        double expansion = player.getClientVersion().isOlderThan(ClientVersion.V_1_9) ? 0.1f : 0;
        maxHorizontalDisplacement = 0.3001 + expansion;
        minVerticalDisplacement = -0.0001 - expansion;
        maxVerticalDisplacement = 1.8001 + expansion;
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (exempt) return;

        if (event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY)) {
            final WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) return;
            Vector3d targetVector = wrapper.getLocation();
            if (targetVector == null) return;

            if (!Double.isFinite(targetVector.x) || !Double.isFinite(targetVector.y) || !Double.isFinite(targetVector.z)) {
                flagAndAlert(String.format("%s/%s/%s", targetVector.x, targetVector.y, targetVector.z));
                return;
            }

            final PacketEntity packetEntity = player.compensatedEntities.getEntity(wrapper.getEntityId());
            if (packetEntity == null) {
                return;
            }

            if (!EntityTypes.PLAYER.equals(packetEntity.type)) {
                return;
            }

            final float scale = (float) packetEntity.getAttributeValue(Attributes.SCALE);
            if (targetVector.y > (minVerticalDisplacement * scale) && targetVector.y < (maxVerticalDisplacement * scale)
                    && Math.abs(targetVector.x) < (maxHorizontalDisplacement * scale)
                    && Math.abs(targetVector.z) < (maxHorizontalDisplacement * scale)) {
                return;
            }

            final String verbose = String.format("%.5f/%.5f/%.5f",
                    targetVector.x, targetVector.y, targetVector.z);
            flagAndAlert(verbose);
        }
    }
}
