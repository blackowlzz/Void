package ac.voidac.checks.impl.packetorder;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PostPredictionCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;

import java.util.ArrayDeque;

@CheckData(name = "PacketOrderK", stableKey = "void.packetorder.inventory_open_order", experimental = true)
public class PacketOrderK extends Check implements PostPredictionCheck {
    public PacketOrderK(final VoidPlayer player) {
        super(player);
    }

    private final ArrayDeque<String> flags = new ArrayDeque<>();

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLIENT_STATUS
                && player.packetOrderProcessor.isLegacyInventoryClient()) {
            if (new WrapperPlayClientClientStatus(event).getAction() == WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT) {
                if (player.packetOrderProcessor.isClickingInInventory() || player.packetOrderProcessor.isClosingInventory()) {
                    String verbose = "open, clicking=" + player.packetOrderProcessor.isClickingInInventory() + ", closing=" + player.packetOrderProcessor.isClosingInventory();
                    if (!player.canSkipTicks()) {
                        flagAndAlert(verbose);
                    } else {
                        flags.add(verbose);
                    }
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW || event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            if (player.packetOrderProcessor.isOpeningInventory()) {
                String verbose = event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW ? "click" : "close";
                if (!player.canSkipTicks()) {
                    if (flagAndAlert(verbose) && shouldModifyPackets() && event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                } else {
                    flags.add(verbose);
                }
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
