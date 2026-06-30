package ac.voidac.checks.impl.multiactions;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

@CheckData(name = "MultiActionsD", stableKey = "void.multiactions.inventory_close_while_moving", description = "Closed inventory while moving")
public class MultiActionsD extends Check implements PacketCheck {
    public MultiActionsD(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLOSE_WINDOW) return;
        if (player.serverOpenedInventoryThisTick) return;

        String verbose = MultiActionsC.getVerbose(player);
        if (verbose.isEmpty()) return;

        // 1.12.2+ clients force-close their inventory when passing through a nether portal
        // while moving, which is vanilla behavior and not a cheat.
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_12_2) && player.isInNetherPortal) return;

        // Don't cancel this packet, because it won't do anything except for making chests
        // look like they are still open (desynced),
        // and it can cause incompatibility issues with plugins
        flagAndAlert(verbose);
    }
}
