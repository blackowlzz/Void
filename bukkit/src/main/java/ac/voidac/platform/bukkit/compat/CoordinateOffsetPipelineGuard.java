package ac.voidac.platform.bukkit.compat;

import ac.voidac.VoidAPI;
import ac.voidac.utils.netty.PipelineOrder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import io.github.retrooper.packetevents.injector.handlers.PacketEventsDecoder;
import io.github.retrooper.packetevents.injector.handlers.PacketEventsEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

public final class CoordinateOffsetPipelineGuard extends PacketListenerAbstract {
    private static final String CONFIG_KEY = "coordinate-offset-compat";
    private static final String DECODER_PREFIX = "pe-decoder-";
    private static final String ENCODER_PREFIX = "pe-encoder-";

    private final PacketTypeCommon relocationPacket = relocationPacket();

    public CoordinateOffsetPipelineGuard() {
        super(PacketListenerPriority.LOWEST);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != relocationPacket) return;
        if (!(event.getChannel() instanceof Channel channel)) return;
        if (!VoidAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse(CONFIG_KEY, false)) return;

        ChannelPipeline pipeline = channel.pipeline();
        PipelineOrder.moveBehindForeign(pipeline, PacketEvents.DECODER_NAME, DECODER_PREFIX,
                handler -> new PacketEventsDecoder((PacketEventsDecoder) handler));
        PipelineOrder.moveBehindForeign(pipeline, PacketEvents.ENCODER_NAME, ENCODER_PREFIX,
                handler -> new PacketEventsEncoder(handler));
    }

    private static PacketTypeCommon relocationPacket() {
        boolean hasConfigurationPhase = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_20_2);
        return hasConfigurationPhase ? PacketType.Configuration.Server.CONFIGURATION_END : PacketType.Play.Server.JOIN_GAME;
    }
}
