package ac.voidac.internal.plugin.resolver;

import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * A functional interface responsible for attempting to resolve a generic context object
 * into a {@link VoidPlugin}.
 * <p>
 * Implementations of this are provided by the core VoidAC platform module (e.g., for Bukkit, Fabric)
 * and registered with the central VoidExtensionManager.
 */
@FunctionalInterface
public interface VoidExtensionResolver {

    /**
     * Attempts to resolve the given context object into a VoidPlugin.
     *
     * @param context The context object to resolve (e.g., a Bukkit Plugin, a Plugin Class, a Fabric Mod).
     * @return A VoidPlugin if this resolver supports the context type, otherwise null.
     */
    @Nullable VoidPlugin resolve(@NotNull Object context);

}
