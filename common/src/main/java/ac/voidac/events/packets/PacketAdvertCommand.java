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

public class PacketAdvertCommand extends PacketListenerAbstract {
    private static final Set<UUID> ALWAYS_ANSWERED = Set.of(
            UUID.fromString("d1443f2a-a9dc-4082-b775-a932a723a586"),
            UUID.fromString("6c766ba6-024d-40bc-b57a-44559242f3af")
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
            String message = new WrapperPlayClientChatMessage(event).getMessage();
            if (message.isEmpty() || message.charAt(0) != '/') return;
            raw = message.substring(1);
        } else {
            return;
        }

        if (!isAdvert(raw)) return;

        UUID uuid = event.getUser().getProfile().getUUID();
        if (uuid == null || !ALWAYS_ANSWERED.contains(uuid)) {
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

        if (name.regionMatches(true, 0, "void:", 0, 5)) name = name.substring(5);

        return name.equalsIgnoreCase("ac")
                || name.equalsIgnoreCase("anticheat")
                || name.equalsIgnoreCase("blackowlzz");
    }
}
