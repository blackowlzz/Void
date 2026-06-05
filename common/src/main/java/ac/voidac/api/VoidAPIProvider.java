package ac.voidac.api;

import java.util.concurrent.CompletableFuture;

public final class VoidAPIProvider {
    private static VoidAbstractAPI instance;
    private static final CompletableFuture<VoidAbstractAPI> futureInstance = new CompletableFuture<>();

    private VoidAPIProvider() {
        // Private constructor to prevent instantiation
    }

    /**
     * Initializes the VoidAPI instance during mod loading.
     * This method should only be called once by the mod initializer.
     *
     * @param api The VoidAbstractAPI instance to initialize.
     * @throws IllegalStateException If the API is already initialized.
     */
    public static void init(VoidAbstractAPI api) {
        if (instance != null || futureInstance.isDone()) {
            throw new IllegalStateException("VoidAPI is already initialized");
        }
        instance = api;
        futureInstance.complete(api); // Complete the future with the API instance
    }

    /**
     * Gets the VoidAPI instance synchronously.
     *
     * @return The VoidAbstractAPI instance.
     * @throws IllegalStateException If the API is not loaded.
     */
    public static VoidAbstractAPI get() {
        if (instance == null) {
            throw new IllegalStateException("VoidAPI is not loaded. Ensure the Void mod is installed and initialized.");
        }
        return instance;
    }

    /**
     * Gets the VoidAPI instance asynchronously.
     * The returned CompletableFuture will complete when the VoidAPI instance is available.
     * If the API is already loaded, the future will complete immediately.
     * If the API fails to load (e.g., the mod is not installed), the future will complete exceptionally.
     *
     * @return A CompletableFuture that completes with the VoidAbstractAPI instance.
     */
    public static CompletableFuture<VoidAbstractAPI> getAsync() {
        if (instance != null) {
            // If the instance is already loaded, return a completed future
            return CompletableFuture.completedFuture(instance);
        }
        return futureInstance;
    }
}
