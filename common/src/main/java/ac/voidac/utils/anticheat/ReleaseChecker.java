package ac.voidac.utils.anticheat;

import ac.voidac.utils.common.arguments.CommonVoidArguments;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class ReleaseChecker {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/voidac/version";
    private static final Set<String> PROXY_LOADERS = Set.of("bungeecord", "velocity", "waterfall");
    private static final long CACHE_MILLIS = 600_000L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.of(CommonVoidArguments.URL_TIMEOUT.value(), ChronoUnit.MILLIS))
            .build();

    private static volatile ReleaseStatus cached;
    private static volatile String cachedFor;
    private static volatile long cachedAt;

    public @NotNull ReleaseStatus check(@NotNull String current) {
        ReleaseStatus status = cached;
        if (status != null && current.equals(cachedFor) && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
            return status;
        }
        ReleaseStatus fetched = fetch(current);
        if (fetched.kind() != ReleaseStatus.Kind.UNKNOWN) {
            cached = fetched;
            cachedFor = current;
            cachedAt = System.currentTimeMillis();
        }
        return fetched;
    }

    public @NotNull List<String> pluginVersions(@NotNull JsonArray modrinthVersions) {
        Set<String> versions = new LinkedHashSet<>();
        for (JsonElement element : modrinthVersions) {
            JsonObject version = element.getAsJsonObject();
            if (!isProxyOnly(version)) versions.add(version.get("version_number").getAsString());
        }
        return List.copyOf(versions);
    }

    private boolean isProxyOnly(JsonObject version) {
        JsonElement loaders = version.get("loaders");
        if (loaders == null || !loaders.isJsonArray() || loaders.getAsJsonArray().isEmpty()) return false;
        for (JsonElement loader : loaders.getAsJsonArray()) {
            if (!PROXY_LOADERS.contains(loader.getAsString())) return false;
        }
        return true;
    }

    private ReleaseStatus fetch(String current) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODRINTH_API))
                    .GET()
                    .header("User-Agent", "VoidAC/" + current + " (update-checker)")
                    .timeout(Duration.of(CommonVoidArguments.URL_TIMEOUT.value(), ChronoUnit.MILLIS))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return ReleaseStatus.UNKNOWN;

            return ReleaseStatus.evaluate(current, pluginVersions(JsonParser.parseString(response.body()).getAsJsonArray()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ReleaseStatus.UNKNOWN;
        } catch (Exception exception) {
            return ReleaseStatus.UNKNOWN;
        }
    }
}
