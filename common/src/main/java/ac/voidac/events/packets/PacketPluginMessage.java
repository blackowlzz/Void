package ac.voidac.events.packets;

import ac.voidac.VoidAPI;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import ac.voidac.utils.common.arguments.CommonVoidArguments;
import ac.voidac.utils.data.webhook.discord.Embed;
import ac.voidac.utils.data.webhook.discord.WebhookMessage;
import ac.voidac.utils.viaversion.ViaVersionUtil;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;


public class PacketPluginMessage extends PacketListenerAbstract {

    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            checkChannel(event.getUser(), packet.getChannelName());
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage packet = new WrapperConfigClientPluginMessage(event);
            checkChannel(event.getUser(), packet.getChannelName());
        }
    }

    private void checkChannel(User user, String channelName) {
        if (!"vv:proxy_details".equals(channelName)) return;
        final boolean usingProxy = ProxyAlertMessenger.isUsingProxy();
        final boolean allowViaProxy = VoidAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("via-proxy.allow", false);

        if (usingProxy) {
            LogUtil.warn(
                    user.getName() + " seems to have connected through a proxy running ViaVersion. "
                            + "Having ViaVersion installed on the proxy is incompatible with Void and causes many issues. "
                            + "Please remove ViaVersion from your proxy server and install it on your backend servers instead."
            );
        }

        // kick if: the JVM override or config says to kick, AND (no proxy configured OR ViaVersion is on backend)
        if (!allowViaProxy && CommonVoidArguments.KICK_ON_VIA_PROXY.value() && (!usingProxy || ViaVersionUtil.isAvailable)) {
            LogUtil.warn(user.getName() + " is being disconnected for sending ViaVersion proxy data.");
            try {
                WrapperPlayServerDisconnect disconnect = new WrapperPlayServerDisconnect(
                        MessageUtil.miniMessage(VoidAPI.INSTANCE.getConfigManager().getDisconnectPacketError())
                );
                user.sendPacket(disconnect);
            } catch (Exception e) {
                LogUtil.warn("Failed to send disconnect packet to kick " + user.getName() + "!");
            }
            user.closeConnection();
            return;
        }

        // Player is allowed through — mark them and optionally disable setbacks to reduce false positives
        VoidPlayer voidPlayer = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
        if (voidPlayer != null) {
            voidPlayer.connectedThroughViaProxy = true;
            if (VoidAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("via-proxy.disable-setbacks", true)) {
                voidPlayer.noSetbackPermission = true;
            }
            notifyStaff(user.getName());
            sendWebhookNotification(user.getName());
        }
    }

    private static void notifyStaff(String playerName) {
        Component message = MessageUtil.miniMessage(
                "%prefix% <yellow>⚠ <white>" + playerName
                + " <gray>is connected through a ViaProxy. Compatibility issues and false flags may occur."
        );
        VoidAPI.INSTANCE.getAlertManager().sendAlert(message, null);
    }

    private static void sendWebhookNotification(String playerName) {
        if (VoidAPI.INSTANCE.getDiscordManager().isDisabled()) return;
        Embed embed = new Embed("**" + playerName + "** connected through a proxy running ViaVersion.\n"
                + "This may cause compatibility issues and false flags.")
                .color(0xFFC400)  // yellow warning color
                .title("⚠ ViaProxy detected");
        VoidAPI.INSTANCE.getDiscordManager().sendWebhookMessage(new WebhookMessage().addEmbeds(embed));
    }

}
