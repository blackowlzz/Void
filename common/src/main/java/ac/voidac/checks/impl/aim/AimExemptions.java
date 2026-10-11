package ac.voidac.checks.impl.aim;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.BedrockPlayers;
import com.github.retrooper.packetevents.protocol.player.GameMode;

final class AimExemptions {
    private AimExemptions() {
    }

    static boolean applies(VoidPlayer player) {
        return !player.disableVoid
                && !BedrockPlayers.isFloodgate(player.uuid)
                && player.gamemode != GameMode.SPECTATOR
                && !player.packetStateData.lastPacketWasTeleport
                && !player.packetStateData.lastPacketWasOnePointSeventeenDuplicate
                && !player.packetStateData.horseInteractCausedForcedRotation
                && !player.vehicleData.wasVehicleSwitch
                && player.compensatedEntities.self.getRiding() == null;
    }
}
