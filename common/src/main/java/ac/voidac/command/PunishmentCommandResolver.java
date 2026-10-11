package ac.voidac.command;

import java.util.Locale;
import java.util.Optional;

public final class PunishmentCommandResolver {
    private PunishmentCommandResolver() {
    }

    public static Optional<String> resolveUnbanCommand(String template, String playerName) {
        if (template == null || template.isBlank()) {
            return Optional.empty();
        }

        String command = template.replace("{player}", playerName).trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            return Optional.empty();
        }

        String normalized = command.toLowerCase(Locale.ROOT);
        if (normalized.equals("void unban") || normalized.startsWith("void unban ")
                || normalized.equals("voidac unban") || normalized.startsWith("voidac unban ")) {
            return Optional.empty();
        }
        return Optional.of(command);
    }
}
