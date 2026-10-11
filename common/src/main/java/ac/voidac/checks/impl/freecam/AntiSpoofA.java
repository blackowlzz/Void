package ac.voidac.checks.impl.freecam;

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
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@CheckData(name = "AntiSpoofA", stableKey = "void.antispoof.cheat_client",
        description = "Known cheat client detected via brand or channel",
        decay = 0, setback = -1)
public class AntiSpoofA extends Check implements PacketCheck {
    private static final List<String> DEFAULT_BRANDS = List.of(
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
            "vape"
    );

    private static final List<String> DEFAULT_CHANNELS = List.of(
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
            "xaero"
    );

    private static final String FABRIC_REGISTER_CHANNEL_V1 = "minecraft:register";
    private static final String FABRIC_REGISTER_CHANNEL_V2 = "fabric:register";
    private static final String FABRIC_REGISTER_CHANNEL_LEGACY = "REGISTER";

    private static final List<String> DEFAULT_REGISTRATIONS = List.of(
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
            "rise:"
    );

    private static final List<String> DEFAULT_PAYLOAD_KEYWORDS = List.of(
            "meteor-client",
            "meteorclient",
            "liquidbounce",
            "aristois",
            "rusherhack"
    );

    private String[] blockedBrands = new String[0];
    private String[] blockedChannels = new String[0];
    private String[] blockedRegistrations = new String[0];
    private String[] blockedPayloadKeywords = new String[0];
    private boolean detected = false;
    private boolean autoKick = true;
    private String kickMessage = "<red>You have been kicked for using a blacklisted client.";

    public AntiSpoofA(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        autoKick = config.getBooleanElse("anti-spoof.auto-kick",
                config.getBooleanElse("AntiSpoofA.auto-kick", true));
        kickMessage = config.getStringElse("anti-spoof.kick-message",
                config.getStringElse("AntiSpoofA.kick-message",
                        "<red>You have been kicked for using a blacklisted client."));
        blockedBrands = patterns(config, "anti-spoof.blocked-brands", DEFAULT_BRANDS);
        blockedChannels = patterns(config, "anti-spoof.blocked-channels", DEFAULT_CHANNELS);
        blockedRegistrations = patterns(config, "anti-spoof.blocked-registrations", DEFAULT_REGISTRATIONS);
        blockedPayloadKeywords = patterns(config, "anti-spoof.blocked-payload-keywords", DEFAULT_PAYLOAD_KEYWORDS);
    }

    private static String[] patterns(ConfigManager config, String key, List<String> defaults) {
        return normalizePatterns(config.getStringListElse(key, defaults));
    }

    static String[] normalizePatterns(List<String> configured) {
        return configured.stream()
                .filter(Objects::nonNull)
                .map(pattern -> pattern.trim().toLowerCase(Locale.ROOT))
                .filter(pattern -> !pattern.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    private static @Nullable String firstMatch(String value, String[] patterns) {
        for (String pattern : patterns) {
            if (value.contains(pattern)) return pattern;
        }
        return null;
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

        if (firstMatch(lowerChannel, blockedChannels) != null) {
            onCheatDetected("channel=" + channel);
            return;
        }

        if (lowerChannel.equals("minecraft:brand") || lowerChannel.equals("mc|brand")) {
            checkBrand(data);
            return;
        }

        if (lowerChannel.equals(FABRIC_REGISTER_CHANNEL_V1)
                || lowerChannel.equals(FABRIC_REGISTER_CHANNEL_V2)
                || channel.equals(FABRIC_REGISTER_CHANNEL_LEGACY)) {
            checkRegisterPayload(data);
            return;
        }

        if (data != null && data.length > 0 && data.length <= 2048) {
            scanPayload(data, channel);
        }
    }

    private void checkBrand(byte[] data) {
        if (data == null || data.length == 0 || data.length > 256) return;

        if (data.length > 1) {
            String brand = new String(data, 1, data.length - 1, StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            if (firstMatch(brand, blockedBrands) != null) {
                onCheatDetected("brand=" + brand);
                return;
            }
        }
        String brandRaw = new String(data, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (firstMatch(brandRaw, blockedBrands) != null) {
            onCheatDetected("brand=" + brandRaw);
        }
    }

    private void checkRegisterPayload(byte[] data) {
        if (data == null || data.length == 0) return;

        int start = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == 0) {
                if (i > start) {
                    String channelName = new String(data, start, i - start, StandardCharsets.UTF_8)
                            .toLowerCase(Locale.ROOT).trim();
                    if (firstMatch(channelName, blockedRegistrations) != null) {
                        onCheatDetected("register=" + channelName);
                        return;
                    }
                }
                start = i + 1;
            }
        }
    }

    private void scanPayload(byte[] data, String sourceChannel) {
        String keyword = firstMatch(new String(data, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT), blockedPayloadKeywords);
        if (keyword != null) {
            onCheatDetected("payload=" + keyword + " ch=" + sourceChannel);
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
