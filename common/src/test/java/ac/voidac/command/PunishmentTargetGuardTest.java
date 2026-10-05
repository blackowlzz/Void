package ac.voidac.command;

import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.platform.api.sender.Sender;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentTargetGuardTest {

    private static final UUID OWNER_UUID = UUID.fromString("268dac22-a6c6-4ff4-b620-a4e16bcf20a7");

    @Test
    void blocksPlayerByUuid() {
        Sender sender = new TestSender("Owner", OWNER_UUID, true);

        assertTrue(PunishmentTargetGuard.isSelf(sender, OWNER_UUID, "DifferentName"));
    }

    @Test
    void blocksPlayerByNameWhenOfflineModeChangesTheUuid() {
        Sender sender = new TestSender("Owner", OWNER_UUID, true);

        assertTrue(PunishmentTargetGuard.isSelf(sender, UUID.randomUUID(), "owner"));
    }

    @Test
    void allowsConsoleAndOtherPlayers() {
        Sender console = new TestSender(Sender.CONSOLE_NAME, Sender.CONSOLE_UUID, false);
        Sender sender = new TestSender("Owner", OWNER_UUID, true);

        assertFalse(PunishmentTargetGuard.isSelf(console, Sender.CONSOLE_UUID, Sender.CONSOLE_NAME));
        assertFalse(PunishmentTargetGuard.isSelf(sender, UUID.randomUUID(), "AnotherPlayer"));
    }

    private record TestSender(String name, UUID uniqueId, boolean player) implements Sender {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public UUID getUniqueId() {
            return uniqueId;
        }

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public void sendMessage(Component message) {
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public boolean hasPermission(String permission, boolean defaultIfUnset) {
            return true;
        }

        @Override
        public void performCommand(String commandLine) {
        }

        @Override
        public boolean isConsole() {
            return !player;
        }

        @Override
        public boolean isPlayer() {
            return player;
        }

        @Override
        public Object getNativeSender() {
            return this;
        }

        @Override
        public PlatformPlayer getPlatformPlayer() {
            return null;
        }
    }
}
