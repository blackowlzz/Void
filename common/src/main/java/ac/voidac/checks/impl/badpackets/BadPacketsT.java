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

    private final double maxHorizontalDisplacement;
    private final double minVerticalDisplacement;
    private final double maxVerticalDisplacement;

    public BadPacketsT(final VoidPlayer player) {
        super(player);
        // 1.7 and 1.8 seem to have different hitbox "expansion" values than 1.9+
        // https://github.com/VoidAnticheat/Void/pull/1274#issuecomment-1872458702
        // https://github.com/VoidAnticheat/Void/pull/1274#issuecomment-1872533497
        double expansion = player.getClientVersion().isOlderThan(ClientVersion.V_1_9) ? 0.1 : 0;
        maxHorizontalDisplacement = 0.3001 + expansion;
        minVerticalDisplacement = -0.0001 - expansion;
        maxVerticalDisplacement = 1.8001 + expansion;
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY)) {
            final WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            // Only INTERACT_AT actually has an interaction vector
            if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) return;
            Vector3d targetVector = wrapper.getLocation();
            if (targetVector == null) return; // shouldn't ever happen, but whatever

            final PacketEntity packetEntity = player.compensatedEntities.getEntity(wrapper.getEntityId());
            // Don't continue if the compensated entity hasn't been resolved
            if (packetEntity == null) {
                return;
            }

            // Make sure our target entity is actually a player (Player NPCs work too)
            if (!EntityTypes.PLAYER.equals(packetEntity.type)) {
                // We can't check for any entity that is not a player
                return;
            }

            // Perform the interaction vector check (player-only for now)
            //  30/12/2023 - Expansions differ in 1.9+
            final float scale = (float) packetEntity.getAttributeValue(Attributes.SCALE);
            if (targetVector.y > (minVerticalDisplacement * scale) && targetVector.y < (maxVerticalDisplacement * scale)
                    && Math.abs(targetVector.x) < (maxHorizontalDisplacement * scale)
                    && Math.abs(targetVector.z) < (maxHorizontalDisplacement * scale)) {
                return;
            }

            // Log the vector
            final String verbose = String.format("%.5f/%.5f/%.5f",
                    targetVector.x, targetVector.y, targetVector.z);
            // We could pretty much ban the player at this point
            flagAndAlert(verbose);
        }
    }
}
