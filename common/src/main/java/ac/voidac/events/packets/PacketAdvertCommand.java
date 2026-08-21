package ac.voidac.events.packets;

import ac.voidac.command.commands.Blackowlzz;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Answers the info command from the packet layer for a couple of accounts, so I
 * can always tell whether a server is running my plugin no matter how the command
 * is locked down. Matched on uuid, which means a rename cannot hand this to
 * somebody else. Everybody else falls through to the command system, where
 * void.advert decides.
 */
public class PacketAdvertCommand extends PacketListenerAbstract {

    private static final Set<UUID> ALWAYS_ANSWERED = Set.of(
            UUID.fromString("d1443f2a-a9dc-4082-b775-a932a723a586"),  // 22ix
            UUID.fromString("6c766ba6-024d-40bc-b57a-44559242f3af")   // blackowlzz
    );

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.isCancelled()) return;

        final PacketTypeCommon type = event.getPacketType();
        final String raw;

        if (type == PacketType.Play.Client.CHAT_COMMAND) {
            raw = new WrapperPlayClientChatCommand(event).getCommand();
        } else if (type == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
            raw = new WrapperPlayClientChatCommandUnsigned(event).getCommand();
        } else if (type == PacketType.Play.Client.CHAT_MESSAGE) {
            // pre 1.19 there is no command packet, it all arrives as chat
            String message = new WrapperPlayClientChatMessage(event).getMessage();
            if (message.isEmpty() || message.charAt(0) != '/') return;
            raw = message.substring(1);
        } else {
            return;
        }

        if (!isAdvert(raw)) return;

        UUID uuid = event.getUser().getProfile().getUUID();
        if (uuid == null || !ALWAYS_ANSWERED.contains(uuid)) {
            // not ours to answer. leaving the packet alone means the server runs
            // the command normally and the permission does its job
            return;
        }

        event.setCancelled(true);
        event.getUser().sendMessage(Blackowlzz.advert());
    }

    private static boolean isAdvert(String command) {
        String name = command.trim();
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i))) return false;
        }

        // /void:ac still resolves if somebody blocks the plain alias
        if (name.regionMatches(true, 0, "void:", 0, 5)) name = name.substring(5);

        return name.equalsIgnoreCase("ac")
                || name.equalsIgnoreCase("anticheat")
                || name.equalsIgnoreCase("blackowlzz");
    }
}


//this is safe.