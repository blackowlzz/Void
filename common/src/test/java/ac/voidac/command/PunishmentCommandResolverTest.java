package ac.voidac.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentCommandResolverTest {

    @Test
    void resolvesExternalUnbanCommand() {
        assertEquals("essentials:unban PlayerOne",
                PunishmentCommandResolver.resolveUnbanCommand("/essentials:unban {player}", "PlayerOne").orElseThrow());
    }

    @Test
    void rejectsBlankAndRecursiveCommands() {
        assertTrue(PunishmentCommandResolver.resolveUnbanCommand("", "PlayerOne").isEmpty());
        assertTrue(PunishmentCommandResolver.resolveUnbanCommand("void unban {player}", "PlayerOne").isEmpty());
        assertTrue(PunishmentCommandResolver.resolveUnbanCommand("voidac unban {player}", "PlayerOne").isEmpty());
    }
}
