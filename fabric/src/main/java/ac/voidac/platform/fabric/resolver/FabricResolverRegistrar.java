package ac.voidac.platform.fabric.resolver;

import ac.voidac.api.plugin.BasicVoidPlugin;
import ac.voidac.api.plugin.VoidPlugin;
import ac.voidac.internal.plugin.resolver.VoidExtensionManager;
import ac.voidac.platform.fabric.utils.message.JULoggerFactory;
import lombok.RequiredArgsConstructor;
import net.fabricmc.api.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.metadata.Person;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the registration of Fabric-specific resolvers with the VoidExtensionManager.
 * This class is designed to be instantiated once during plugin startup.
 */
@RequiredArgsConstructor
public final class FabricResolverRegistrar {

    // Cache to ensure we only create one VoidPlugin wrapper per Fabric ModContainer.
    private final Map<ModContainer, VoidPlugin> modContainerCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, VoidPlugin> classCache = new ConcurrentHashMap<>();
    private final Map<Object, VoidPlugin> entrypointCache = new ConcurrentHashMap<>();

    /**
     * Registers all the Fabric-specific resolvers in order of performance (fastest to slowest).
     */
    public void registerAll(VoidExtensionManager extensionManager) {
        extensionManager.setFailureHandler(this::createFailureException);
        extensionManager.registerResolver(this::resolveModContainer);
        extensionManager.registerResolver(this::resolveStringId);
        extensionManager.registerResolver(this::resolveEntrypointInstance);
        extensionManager.registerResolver(this::resolveClass);
    }

    /**
     * Create a shared, reusable function to handle the core logic of
     * converting a Fabric ModContainer to a VoidPlugin wrapper.
     */
    private VoidPlugin resolveMod(ModContainer modContainer) {
        return modContainerCache.computeIfAbsent(modContainer, container -> {
            net.fabricmc.loader.api.metadata.ModMetadata metadata = container.getMetadata();
            String folderName = metadata.getId().equals("void") ? metadata.getName() : metadata.getId();
            return new BasicVoidPlugin(
                    JULoggerFactory.createLogger(metadata.getName()),
                    new File(FabricLoader.getInstance().getConfigDir().toFile(), folderName),
                    metadata.getVersion().getFriendlyString(),
                    metadata.getDescription(),
                    metadata.getAuthors().stream().map(Person::getName).collect(Collectors.toList())
            );
        });
    }

    /**
     * Resolver #0: Direct ModContainer (fastest)
     */
    private VoidPlugin resolveModContainer(Object context) {
        return (context instanceof ModContainer mc) ? resolveMod(mc) : null;
    }

    /**
     * Resolver #1: String Mod ID (very fast)
     */
    private VoidPlugin resolveStringId(Object context) {
        if (context instanceof String modId) {
            // Mod IDs are enforced to always be fully lowercase
            return FabricLoader.getInstance().getModContainer(modId.toLowerCase(Locale.ROOT))
                    .map(this::resolveMod)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Resolver #2: Mod Entrypoint Instance (slower, but cached)
     */
    private VoidPlugin resolveEntrypointInstance(Object context) {
        // We only care about potential entrypoint instances
        if (context instanceof ModInitializer || context instanceof PreLaunchEntrypoint || context instanceof ClientModInitializer || context instanceof DedicatedServerModInitializer) {
            return entrypointCache.computeIfAbsent(context, this::findEntrypoint);
        }
        return null;
    }

    private VoidPlugin findEntrypoint(Object key) {
        VoidPlugin result;
        if ((result = findEntrypoint(key, ModInitializer.class, "main")) != null) return result;
        if ((result = findEntrypoint(key, PreLaunchEntrypoint.class, "preLaunch")) != null) return result;
        if ((result = findEntrypoint(key, ClientModInitializer.class, "client")) != null) return result;
        if ((result = findEntrypoint(key, DedicatedServerModInitializer.class, "server")) != null) return result;
        return null;
    }

    private <T> VoidPlugin findEntrypoint(Object context, Class<T> entrypointClass, String entrypointKey) {
        if (entrypointClass.isInstance(context)) {
            for (EntrypointContainer<T> container : FabricLoader.getInstance().getEntrypointContainers(entrypointKey, entrypointClass)) {
                if (container.getEntrypoint() == context) {
                    return resolveMod(container.getProvider());
                }
            }
        }
        return null;
    }

    /**
     * Resolver #3: Class object (slowest - involves I/O, but cached)
     */
    private VoidPlugin resolveClass(Object context) {
        if (context instanceof Class<?> clazz) {
            return classCache.computeIfAbsent(clazz, this::findClassProvider);
        }
        return null;
    }

    private VoidPlugin findClassProvider(Class<?> c) {
        try {
            // 1. Get the path to the physical JAR/directory the class was loaded from.
            // This is our ground truth.
            java.security.CodeSource codeSource = c.getProtectionDomain().getCodeSource();
            if (codeSource == null) return null;
            java.net.URL sourceUrl = codeSource.getLocation();
            if (sourceUrl == null) return null;
            Path sourcePath = Paths.get(sourceUrl.toURI());

            // 2. Iterate through all mods and check if the class's source path
            // matches the physical path of the mod's container.
            for (ModContainer modContainer : FabricLoader.getInstance().getAllMods()) {
                for (Path modRootPath : modContainer.getRootPaths()) {
                    URI modUri = modRootPath.toUri();
                    Path modPhysicalPath = null;

                    // 3. Determine the mod's physical path based on its URI scheme.
                    if ("file".equals(modUri.getScheme())) {
                        modPhysicalPath = modRootPath;
                    } else if ("jar".equals(modUri.getScheme())) {
                        String schemeSpecificPart = modUri.getSchemeSpecificPart();
                        int separatorIndex = schemeSpecificPart.indexOf("!/");
                        if (separatorIndex != -1) {
                            String jarUriString = schemeSpecificPart.substring(0, separatorIndex);
                            modPhysicalPath = Paths.get(new URI(jarUriString));
                        }
                    }

                    // 4. Perform the reliable file comparison.
                    if (modPhysicalPath != null && Files.isSameFile(modPhysicalPath, sourcePath)) {
                        return resolveMod(modContainer);
                    }
                }
            }
        } catch (URISyntaxException | IOException | NullPointerException e) {
            // Fail gracefully.
            return null;
        }
        return null;
    }

    private RuntimeException createFailureException(Object failedContext) {
        String message = """
        Failed to resolve VoidPlugin context from the provided object of type '%s'.

        Please ensure you are passing one of the following:
          - The main instance of your mod (e.g., 'this' from your ModInitializer class).
          - The mod ID as a String (e.g., "my-mod-id").
          - Any Class from your mod's JAR file (e.g., MyListener.class).
          - A pre-existing VoidPlugin instance.
        """.formatted(failedContext.getClass().getName());
        return new IllegalArgumentException(message);
    }
}
