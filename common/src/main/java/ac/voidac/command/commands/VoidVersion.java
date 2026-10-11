package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.ReleaseChecker;
import ac.voidac.utils.anticheat.SemanticVersion;
import ac.voidac.utils.common.arguments.CommonVoidArguments;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

import static ac.voidac.command.render.MessageStyle.*;

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
        sender.sendMessage(info(sc("Running Void ") + "&5" + current));
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
            List<String> releases = ReleaseChecker.pluginVersions(versions);
            if (releases.isEmpty()) return;

            String latest = releases.get(0);
            String current = VoidAPI.INSTANCE.getExternalAPI().getVoidVersion();

            Status status = Status.SemVer.getVersionStatus(current, latest);
            Component msg = switch (status) {
                case AHEAD -> info("&d" + sc("You are running a development build"));
                case UPDATED -> success(sc("Void is up to date"));
                case OUTDATED -> warning(sc("A new release is available") + " &8│ &f" + latest + " &8▸ <click:open_url:'"
                        + MODRINTH_URL + "'><aqua><u>" + MODRINTH_URL + "</u></aqua></click>");
                case UNKNOWN -> error(sc("Could not read this version"));
            };
            updateMessage.set(msg);
            sender.sendMessage(msg);
        } catch (Exception e) {
            sender.sendMessage(error(sc("Could not check the latest version")));
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
                OptionalInt comparison = SemanticVersion.compare(current, latest);
                if (comparison.isEmpty()) return Status.UNKNOWN;
                if (comparison.getAsInt() == 0) return Status.UPDATED;
                return comparison.getAsInt() < 0 ? Status.OUTDATED : Status.AHEAD;
            }
        }
    }
}
