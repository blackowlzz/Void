package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import ac.voidac.utils.common.arguments.CommonVoidArguments;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

public class VoidVersion implements BuildableCommand {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/voidac/version";
    private static final String MODRINTH_URL = "https://modrinth.com/plugin/voidac";
    private static final AtomicReference<Component> updateMessage = new AtomicReference<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.of(CommonVoidArguments.URL_TIMEOUT.value(), ChronoUnit.MILLIS))
            .build();
    private static long lastCheck;

    public static void checkForUpdatesAsync(Sender sender) {
        String current = VoidAPI.INSTANCE.getExternalAPI().getVoidVersion();
        sender.sendMessage(Component.text()
                .append(Component.text("Void trace version: ").color(NamedTextColor.GRAY))
                .append(Component.text(current).color(NamedTextColor.AQUA))
                .build());
        final long now = System.currentTimeMillis();
        if (now - lastCheck < 60000) {
            Component message = updateMessage.get();
            if (message != null) sender.sendMessage(message);
            return;
        }
        lastCheck = now;
        VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(VoidAPI.INSTANCE.getVoidPlugin(), () -> checkForUpdates(sender));
    }

    private static void checkForUpdates(Sender sender) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODRINTH_API))
                    .GET()
                    .header("User-Agent", "VoidAC/" + VoidAPI.INSTANCE.getExternalAPI().getVoidVersion() + " (update-checker)")
                    .timeout(Duration.of(CommonVoidArguments.URL_TIMEOUT.value(), ChronoUnit.MILLIS))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Component msg = updateMessage.get();
                if (msg != null) sender.sendMessage(msg);
                return;
            }

            @SuppressWarnings("deprecation")
            JsonArray versions = new JsonParser().parse(response.body()).getAsJsonArray();
            if (versions.isEmpty()) return;

            String latest = versions.get(0).getAsJsonObject().get("version_number").getAsString();
            String current = VoidAPI.INSTANCE.getExternalAPI().getVoidVersion();

            Status status = Status.SemVer.getVersionStatus(current, latest);
            Component msg = switch (status) {
                case AHEAD ->
                        Component.text("You are walking on a development build of Void").color(NamedTextColor.LIGHT_PURPLE);
                case UPDATED ->
                        Component.text("Void is already at the current edge").color(NamedTextColor.GREEN);
                case OUTDATED -> Component.text()
                        .append(Component.text("A new Void release is stirring!").color(NamedTextColor.AQUA))
                        .append(Component.text(" Version ").color(NamedTextColor.GRAY))
                        .append(Component.text(latest).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC))
                        .append(Component.text(" can be claimed here: ").color(NamedTextColor.GRAY))
                        .append(Component.text(MODRINTH_URL).color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.openUrl(MODRINTH_URL)))
                        .build();
                case UNKNOWN ->
                        Component.text("Void cannot read this version.").color(NamedTextColor.RED);
            };
            updateMessage.set(msg);
            sender.sendMessage(msg);
        } catch (Exception e) {
            sender.sendMessage(Component.text("The void could not check the latest version.").color(NamedTextColor.RED));
            LogUtil.error("Failed to check latest Void version.", e);
        }
    }

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("void", "voidac", "void", "voidac")
                        .literal("version")
                        .permission("void.version")
                        .handler(this::handleVersion)
        );
    }

    private void handleVersion(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        checkForUpdatesAsync(sender);
    }


    @AllArgsConstructor
    private enum Status {
        AHEAD("ahead"),
        UPDATED("updated"),
        OUTDATED("outdated"),
        UNKNOWN("unknown");

        private final String id;

        private static class SemVer {

            public static Status getVersionStatus(String current, String latest) {
                try {
                    var cmp = compareSemver(current, latest);
                    if (cmp == 0) return Status.UPDATED;
                    if (cmp < 0) return Status.OUTDATED;
                    return Status.AHEAD;
                } catch (Exception ignored) {}
                return Status.UNKNOWN;
            }

            public static String normalizeCoreVersion(String version) {
                String trimmed = version.trim();
                String[] dashParts = trimmed.split("-");
                String[] plusParts = dashParts[0].split("\\+");
                return plusParts[0];
            }

            public static int[] parseVersion(String version) {
                String core = normalizeCoreVersion(version);
                if (core.isEmpty()) return null;
                String[] parts = core.split("\\.");
                if (parts.length < 1) return null;

                int major = parseInt(parts[0]);
                int minor = parts.length > 1 ? parseInt(parts[1]) : 0;
                int patch = parts.length > 2 ? parseInt(parts[2]) : 0;

                if (major < 0 || minor < 0 || patch < 0) return null;
                return new int[] { major, minor, patch };
            }

            private static int parseInt(String str) {
                try {
                    return Integer.parseInt(str);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }

            public static int compareSemver(String a, String b) {
                int[] pa = parseVersion(a);
                int[] pb = parseVersion(b);
                if (pa == null || pb == null) return 0;
                for (int i = 0; i < 3; i++) {
                    if (pa[i] < pb[i]) return -1;
                    if (pa[i] > pb[i]) return 1;
                }
                return 0;
            }
        }
    }
}
