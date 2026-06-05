package ac.voidac.checks.impl.chat;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.impl.multiactions.MultiActionsC;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.regex.Pattern;

@CheckData(name = "ChatC", stableKey = "void.chat.moving_while_chatting", description = "Moving while chatting", experimental = true)
public class ChatC extends Check implements PacketCheck {
    public ChatC(VoidPlayer player) {
        super(player);
    }

    // optionally allow cheats like autogg
    private @Nullable Predicate<String> exemptRegex;

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE) {
            // TODO make previa after making wrapper parse by client version instead of server version
            check(new WrapperPlayClientChatMessage(event).getMessage(), event);
        }

        if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
            check("/" + new WrapperPlayClientChatCommandUnsigned(event).getCommand(), event);
        }

        if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
            // TODO make previa after making wrapper parse by client version instead of server version
            check("/" + new WrapperPlayClientChatCommand(event).getCommand(), event);
        }
    }

    private void check(String message, PacketReceiveEvent event) {
        if (exemptRegex != null && exemptRegex.test(message)) {
            return;
        }

        String verbose = MultiActionsC.getVerbose(player);
        if (!verbose.isEmpty() && flagAndAlert(verbose) && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    @Override
    public void onReload(ConfigManager config) {
        String regexString = config.getStringElse(getConfigName() + ".exempt-regex", null);
        exemptRegex = regexString == null ? null : Pattern.compile(regexString).asMatchPredicate();
    }
}
