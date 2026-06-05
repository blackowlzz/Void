package ac.voidac.platform.fabric.utils.thread;

import ac.voidac.VoidAPI;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FabricFutureUtil {
    public static <U> CompletableFuture<U> supplySync(Supplier<U> entityTeleportSupplier) {
        CompletableFuture<U> ret = new CompletableFuture<>();
        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(VoidAPI.INSTANCE.getVoidPlugin(),
                () -> ret.complete(entityTeleportSupplier.get()));
        return ret;
    }
}
