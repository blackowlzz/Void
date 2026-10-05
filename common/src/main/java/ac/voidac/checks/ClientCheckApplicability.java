package ac.voidac.checks;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public final class ClientCheckApplicability {
    private ClientCheckApplicability() {
    }

    public static boolean packetOrderA(ClientVersion version) {
        return version.isOlderThanOrEquals(ClientVersion.V_1_12_2);
    }

    public static boolean packetOrderH(ClientVersion version) {
        return version.isOlderThan(ClientVersion.V_1_21_6);
    }

    public static boolean packetOrderK(ClientVersion version) {
        return version.isOlderThan(ClientVersion.V_1_12);
    }

    public static boolean duplicateSneak(ClientVersion version) {
        return version.isOlderThan(ClientVersion.V_1_21_2);
    }

    public static boolean playerInputAndBundle(ClientVersion version) {
        return version.isNewerThanOrEquals(ClientVersion.V_1_21_2);
    }

    public static boolean boatPaddles(ClientVersion version) {
        return version.isNewerThanOrEquals(ClientVersion.V_1_9);
    }
}
