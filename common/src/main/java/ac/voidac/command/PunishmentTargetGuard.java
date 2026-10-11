package ac.voidac.command;

import ac.voidac.platform.api.sender.Sender;

import java.util.UUID;

public final class PunishmentTargetGuard {
    private PunishmentTargetGuard() {
    }

    public static boolean isSelf(Sender sender, UUID targetUuid, String targetName) {
        if (!sender.isPlayer()) {
            return false;
        }
        if (sender.getUniqueId().equals(targetUuid)) {
            return true;
        }
        return targetName != null && sender.getName().equalsIgnoreCase(targetName);
    }
}
