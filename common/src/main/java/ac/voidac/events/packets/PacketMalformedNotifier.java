package ac.voidac.events.packets;

import ac.voidac.VoidAPI;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A packet the decoder cannot even map gets the connection dropped before any
 * check sees it, which is right, but it happens deep in PacketEvents where
 * nothing tells the server who did it. Crasher tools live in exactly that gap.
 * <p>
 * The kick is a disconnect packet like any other, so we catch it on its way out:
 * staff get told, and the player gets our wording instead of two bare words.
 */
public class PacketMalformedNotifier extends PacketListenerAbstract {

    // hardcoded inside PacketEventsDecoder#exceptionCaught
    private static final String PACKET_EVENTS_REASON = "Invalid packet";

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.DISCONNECT) return;

        try {
            notifyMalformed(event);
        } catch (Throwable failed) {
            // never let a broken notifier take the disconnect down with it
            LogUtil.error("Malformed packet notifier failed", failed);
        }
    }

    private void notifyMalformed(PacketSendEvent event) {

        final WrapperPlayServerDisconnect packet = new WrapperPlayServerDisconnect(event);
        // read the content off the component itself. adventure's plain serializer
        // is a separate artifact and is not in our shaded jar, asking for it here
        // gets you a NoClassDefFoundError on a thread nobody is watching
        if (!(packet.getReason() instanceof TextComponent reason)
                || !PACKET_EVENTS_REASON.equals(reason.content())) {
            return;
        }

        final VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
        final String name = player != null
                ? player.getName()
                : event.getUser().getProfile().getName();

        packet.setReason(MessageUtil.miniMessage(VoidAPI.INSTANCE.getConfigManager().getDisconnectPacketError()));
        event.markForReEncode(true);

        String format = VoidAPI.INSTANCE.getConfigManager().getConfig().getStringElse(
                "malformed-packet-format",
                "%prefix% &f%player% &7sent a packet the server could not read and was kicked &8(&7possible crasher&8)");
        Component alert = MessageUtil.miniMessage(format.replace("%player%", name == null ? "unknown" : name));
        if (player != null) alert = MessageUtil.replacePlaceholders(player, alert);

        VoidAPI.INSTANCE.getAlertManager().sendAlert(alert, null);
        LogUtil.warn("Kicked " + name + " for sending a malformed packet");
    }
}
