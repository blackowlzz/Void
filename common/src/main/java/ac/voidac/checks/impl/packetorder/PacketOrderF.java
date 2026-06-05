package ac.voidac.checks.impl.packetorder;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PostPredictionCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

import java.util.ArrayDeque;

@CheckData(name = "PacketOrderF", stableKey = "void.packetorder.input_tick_to_sneak_sprint_order", experimental = true)
public class PacketOrderF extends Check implements PostPredictionCheck {
    public PacketOrderF(VoidPlayer player) {
        super(player);
    }

    private final ArrayDeque<String> flags = new ArrayDeque<>();

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY
                || event.getPacketType() == PacketType.Play.Client.ATTACK
                || event.getPacketType() == PacketType.Play.Client.SPECTATE_ENTITY
                || event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                || event.getPacketType() == PacketType.Play.Client.USE_ITEM
                || event.getPacketType() == PacketType.Play.Client.PICK_ITEM
                || event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING
                || (event.getPacketType() == PacketType.Play.Client.CLIENT_STATUS
                && new WrapperPlayClientClientStatus(event).getAction() == WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT)
        ) if (player.packetOrderProcessor.isSprinting() || player.packetOrderProcessor.isSneaking()) {
            String verbose = "action=" + (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY ? "interact"
                    : event.getPacketType() == PacketType.Play.Client.ATTACK ? "attack"
                    : event.getPacketType() == PacketType.Play.Client.SPECTATE_ENTITY ? "spectateEntity"
                    : event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT ? "place"
                    : event.getPacketType() == PacketType.Play.Client.USE_ITEM ? "use"
                    : event.getPacketType() == PacketType.Play.Client.PICK_ITEM ? "pick"
                    : event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING ? "dig"
                    : "openInventory")
                    + ", sprinting=" + player.packetOrderProcessor.isSprinting()
                    + ", sneaking=" + player.packetOrderProcessor.isSneaking();
            if (!player.canSkipTicks()) {
                if (flagAndAlert(verbose) && shouldModifyPackets()) {
                    if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING
                            && !canCancel(new WrapperPlayClientPlayerDigging(event).getAction())
                    ) return; // don't cause a noslow

                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            } else {
                flags.add(verbose);
            }
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (String verbose : flags) {
                flagAndAlert(verbose);
            }
        }

        flags.clear();
    }
}
