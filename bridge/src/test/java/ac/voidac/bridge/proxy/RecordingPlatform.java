package ac.voidac.bridge.proxy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class RecordingPlatform implements ProxyPlatform {

    private final Path directory;
    private final Collection<String> servers;
    final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    final List<UUID> disconnected = new ArrayList<>();
    volatile boolean failOnDisconnect;

    RecordingPlatform(Path directory, String... servers) {
        this.directory = directory;
        this.servers = List.of(servers);
    }

    @Override
    public @NotNull Path dataDirectory() {
        return directory;
    }

    @Override
    public @NotNull Collection<String> serverNames() {
        return servers;
    }

    @Override
    public void disconnect(@NotNull UUID playerUuid, @NotNull String reason) {
        if (failOnDisconnect) throw new IllegalStateException("proxy refused");
        disconnected.add(playerUuid);
    }

    @Override
    public void info(@NotNull String message) {
    }

    @Override
    public void warn(@NotNull String message, @Nullable Throwable error) {
        warnings.add(message);
    }
}
