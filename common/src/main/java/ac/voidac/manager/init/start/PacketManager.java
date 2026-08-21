package ac.voidac.manager.init.start;

import ac.voidac.events.packets.*;
import ac.voidac.events.packets.worldreader.BasePacketWorldReader;
import ac.voidac.events.packets.worldreader.PacketWorldReaderEight;
import ac.voidac.events.packets.worldreader.PacketWorldReaderEighteen;
import ac.voidac.VoidAPI;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;

public class PacketManager implements StartableInitable {
    @Override
    public void start() {
        LogUtil.info("Registering packets...");

        PacketEvents.getAPI().getEventManager().registerListener(new PacketPlayerJoinQuit());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketPingListener());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketPlayerDigging());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketPlayerAttack());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketEntityAction());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketBlockAction());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketSelfMetadataListener());
        PacketEvents.getAPI().getEventManager().registerListener(new BedStateTracker());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketServerTeleport());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketPlayerCooldown());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketPlayerRespawn());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketPlayerTick());
        PacketEvents.getAPI().getEventManager().registerListener(new CheckManagerListener());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketPlayerSteer());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketPluginMessage());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketAdvertCommand());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketMalformedNotifier());

        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
            PacketEvents.getAPI().getEventManager().registerListener(new PacketServerTags());
        }

        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_18)) {
            PacketEvents.getAPI().getEventManager().registerListener(new PacketWorldReaderEighteen());
        } else if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_8_8)) {
            PacketEvents.getAPI().getEventManager().registerListener(new PacketWorldReaderEight());
        } else {
            PacketEvents.getAPI().getEventManager().registerListener(new BasePacketWorldReader());
        }

        PacketEvents.getAPI().getEventManager().registerListener(new ProxyAlertMessenger());
        PacketEvents.getAPI().getEventManager().registerListener(new PacketHidePlayerInfo());

        // config is not loaded yet when PacketEventsInit runs, so the opt-in lands here
        ConfigManager config = VoidAPI.INSTANCE.getConfigManager().getConfig();
        if (config != null && config.getBooleanElse("debug-packet-exceptions", false)) {
            PacketEvents.getAPI().getSettings().fullStackTrace(true);
        }

        PacketEvents.getAPI().init();
    }
}
