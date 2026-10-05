package ac.voidac.utils.latency;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import org.junit.jupiter.api.Test;

class AntiEspManagerTest {

    @Test
    void supportsLegacyLivingEntitySpawnPackets() throws NoSuchMethodException {
        AntiEspManager.class.getDeclaredMethod(
                "handleSpawn",
                PacketSendEvent.class,
                WrapperPlayServerSpawnLivingEntity.class
        );
    }
}
