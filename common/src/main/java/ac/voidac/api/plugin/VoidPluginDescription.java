package ac.voidac.api.plugin;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface VoidPluginDescription {
    String getVersion();

    String getDescription();

    public @NotNull Collection<String> getAuthors();
}
