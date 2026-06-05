package ac.voidac.checks.impl.freecam;

import ac.voidac.VoidAPI;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.MessageUtil;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Detects known cheat clients by analyzing:
 * 1. Client brand strings (some cheat clients expose their identity)
 * 2. Plugin channel registrations (cheat clients register custom channels)
 * 3. Payload content of REGISTER packets (null-separated channel list)
 * 4. General plugin message payload scanning for known cheat identifiers
 *
 * Note: Meteor Client sends "fabric" as its brand on Bukkit servers.
 * Detection relies on channel registrations and payload scanning.
 * REGISTER is parsed per-channel (null-separated) for reliable matching.
 */
@CheckData(name = "AntiSpoofA", stableKey = "void.antispoof.cheat_client",
        description = "Known cheat client detected via brand or channel",
        decay = 0, setback = -1)
public class AntiSpoofA extends Check implements PacketCheck {

    // Known cheat client brand identifiers (lowercase)
    private static final String[] CHEAT_BRANDS = {
            "meteor-client",
            "meteor client",
            "wurst",
            "impact",
            "aristois",
            "inertia",
            "lambda",
            "kami blue",
            "rusherhack",
            "future",
            "konas",
            "phobos",
            "salhack",
            "liquidbounce",
            "fdpclient",
            "sigma",
            "exhibition",
            "ghost",
            "rise",
            "vape",
    };

    // Known cheat client plugin channel identifiers (lowercase, partial match)
    private static final String[] CHEAT_CHANNELS = {
            "meteor-client",
            "meteor:client",
            "meteorclient",
            "wurst:",
            "impact:",
            "aristois:",
            "liquidbounce:",
            "rusherhack:",
            "ghost:",
            "rise:",
            "xaero",
    };

    // Channel names used by Fabric's networking for mod registration
    // Newer Fabric Networking API v2 uses "fabric:register" instead of "minecraft:register"
    private static final String FABRIC_REGISTER_CHANNEL_V1 = "minecraft:register";
    private static final String FABRIC_REGISTER_CHANNEL_V2 = "fabric:register";
    private static final String FABRIC_REGISTER_CHANNEL_LEGACY = "REGISTER";

    // Exact substrings to match inside individual channel names from REGISTER payloads
    private static final String[] CHEAT_CHANNEL_REGISTRATIONS = {
            "meteor-client:",
            "meteor-client",
            "meteor:",
            "meteorclient:",
            "meteorclient",
            "baritone:",
            "wurst:",
            "impact:",
            "aristois:",
            "liquidbounce:",
            "rusherhack:",
            "lambda:",
            "konas:",
            "phobos:",
            "ghost:",
            "rise:",
    };

    // Keywords to match in raw plugin message payloads (client → server, any channel)
    // Only include long/unique identifiers to avoid false positives
    private static final String[] CHEAT_PAYLOAD_KEYWORDS = {
            "meteor-client",
            "meteorclient",
            "liquidbounce",
            "aristois",
            "rusherhack",
    };

    private boolean detected = false;
    private boolean autoKick = true;
    private String kickMessage = "<red>You have been kicked for using a blacklisted client.";

    public AntiSpoofA(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        autoKick = config.getBooleanElse("AntiSpoofA.auto-kick", true);
        kickMessage = config.getStringElse("AntiSpoofA.kick-message",
                "<red>You have been kicked for using a blacklisted client.");
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (detected) return;

        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            handlePluginMessage(packet.getChannelName(), packet.getData());
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage packet = new WrapperConfigClientPluginMessage(event);
            handlePluginMessage(packet.getChannelName(), packet.getData());
        }
    }

    private void handlePluginMessage(String channel, byte[] data) {
        if (channel == null) return;
        String lowerChannel = channel.toLowerCase(Locale.ROOT);

        // 1. Check if the channel itself is a known cheat channel
        for (String cheatChannel : CHEAT_CHANNELS) {
            if (lowerChannel.contains(cheatChannel)) {
                onCheatDetected("channel=" + channel);
                return;
            }
        }

        // 2. Check brand payload
        if (lowerChannel.equals("minecraft:brand") || lowerChannel.equals("mc|brand")) {
            checkBrand(data);
            return;
        }

        // 3. Check REGISTER channel payloads — both modern (fabric:register / minecraft:register)
        //    and legacy (REGISTER). Fabric Networking API v2 uses "fabric:register".
        if (lowerChannel.equals(FABRIC_REGISTER_CHANNEL_V1)
                || lowerChannel.equals(FABRIC_REGISTER_CHANNEL_V2)
                || channel.equals(FABRIC_REGISTER_CHANNEL_LEGACY)) {
            checkRegisterPayload(data);
            return;
        }

        // 4. Scan all other plugin message payloads for cheat client identifiers
        //    (catches any custom messaging Meteor or other clients send)
        if (data != null && data.length > 0 && data.length <= 2048) {
            scanPayload(data, channel);
        }
    }

    private void checkBrand(byte[] data) {
        if (data == null || data.length == 0 || data.length > 256) return;

        // Minecraft brand packet: [VarInt length][UTF-8 string]
        // For short strings (< 128 chars) VarInt is exactly 1 byte — try both forms.
        if (data.length > 1) {
            // Normal form: skip the VarInt length prefix (byte 0)
            String brand = new String(data, 1, data.length - 1, StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            if (matchesBrand(brand)) {
                onCheatDetected("brand=" + brand);
                return;
            }
        }
        // Fallback: try without any prefix (some non-standard implementations)
        String brandRaw = new String(data, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (matchesBrand(brandRaw)) {
            onCheatDetected("brand=" + brandRaw);
        }
    }

    private boolean matchesBrand(String brand) {
        for (String cheatBrand : CHEAT_BRANDS) {
            if (brand.contains(cheatBrand)) return true;
        }
        return false;
    }

    private void checkRegisterPayload(byte[] data) {
        if (data == null || data.length == 0) return;

        // The REGISTER payload is a list of channel names separated by null bytes (\0).
        // Parse each channel individually for precise matching.
        int start = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == 0) {
                if (i > start) {
                    String channelName = new String(data, start, i - start, StandardCharsets.UTF_8)
                            .toLowerCase(Locale.ROOT).trim();
                    for (String cheatReg : CHEAT_CHANNEL_REGISTRATIONS) {
                        if (channelName.contains(cheatReg)) {
                            onCheatDetected("register=" + channelName);
                            return;
                        }
                    }
                }
                start = i + 1;
            }
        }
    }

    private void scanPayload(byte[] data, String sourceChannel) {
        try {
            String content = new String(data, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (String keyword : CHEAT_PAYLOAD_KEYWORDS) {
                if (content.contains(keyword)) {
                    onCheatDetected("payload=" + keyword + " ch=" + sourceChannel);
                    return;
                }
            }
        } catch (Exception ignored) {
            // Non-UTF-8 binary payload — skip silently
        }
    }

    private void onCheatDetected(String verbose) {
        detected = true;

        if (flagAndAlert(verbose) && autoKick) {
            player.disconnect(MessageUtil.miniMessage(
                    MessageUtil.replacePlaceholders(player, kickMessage)));
        }
    }
}
