package ac.voidac.checks.impl.badpackets;

import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.BlockPlaceCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.BlockBreak;
import ac.voidac.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;

@CheckData(name = "BadPacketsH", stableKey = "void.badpackets.unexpected_sequence", description = "Sent unexpected sequence id", experimental = true)
public class BadPacketsH extends BlockPlaceCheck {
    private int lastSequence;
    private final boolean isSupportedVersion = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_19) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19);

    public BadPacketsH(final VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM
                && shouldCancel(new WrapperPlayClientUseItem(event).getSequence())) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (shouldCancel(place.sequence) && shouldCancel()) {
            place.resync();
        }
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        switch (blockBreak.action) {
            case START_DIGGING, FINISHED_DIGGING -> {
                if (shouldCancel(blockBreak.sequence)) {
                    blockBreak.cancel();
                }
            }
            case CANCELLED_DIGGING -> { // other actions will be checked by BadPacketsL
                if (blockBreak.sequence != 0 && flagAndAlert("expected=0, id=" + blockBreak.sequence) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            }
        }
    }

    public boolean shouldCancel(int sequence) {
        int expected = lastSequence + 1;
        lastSequence = sequence;
        return isSupportedVersion && sequence != expected
                && flagAndAlert("expected=" + expected + ", id=" + sequence)
                && shouldModifyPackets();
    }

    public void onWorldChange() {
        lastSequence = 0;
    }
}
