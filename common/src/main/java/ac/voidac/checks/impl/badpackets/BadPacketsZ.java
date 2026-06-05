package ac.voidac.checks.impl.badpackets;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

@CheckData(name = "BadPacketsZ", stableKey = "void.badpackets.duplicate_player_input", experimental = true)
public class BadPacketsZ extends Check implements PacketCheck {
    private boolean sent;

    public BadPacketsZ(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            sent = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
            if (sent) {
                flagAndAlert();
            }

            sent = true;
        }
    }
}
