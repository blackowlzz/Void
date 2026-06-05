package ac.voidac.api.event.events;

import ac.voidac.api.event.EventChannel;
import ac.voidac.api.event.VoidEvent;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class VoidReloadEvent extends VoidEvent<VoidReloadEvent.Channel> {
    private boolean success;

    /** Pool constructor — fields populated via {@link #init}. */
    public VoidReloadEvent() {
        super(true); // Async
    }

    public VoidReloadEvent(boolean success) {
        super(true); // Async
        this.success = success;
    }

    @ApiStatus.Internal
    public void init(boolean success) {
        resetForReuse();
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    @FunctionalInterface
    public interface Handler {
        void onReload(boolean success);
    }

    public static final class Channel extends EventChannel<VoidReloadEvent, Handler> {
        private final ThreadLocal<VoidReloadEvent> legacyPool = ThreadLocal.withInitial(VoidReloadEvent::new);

        public Channel() {
            super(VoidReloadEvent.class, Handler.class);
        }

        public void onReload(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribe(handler, 0, false, plugin, null);
        }

        public void onReload(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribe(handler, priority, false, plugin, null);
        }

        /** @deprecated resolve your context once at plugin enable — {@code api.getVoidPlugin(this)} — and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onReload(@NotNull Object pluginContext, @NotNull Handler handler) {
            onReload(resolvePlugin(pluginContext), handler);
        }

        /** @deprecated see {@link #onReload(Object, Handler)}. */
        @Deprecated
        public void onReload(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            onReload(resolvePlugin(pluginContext), handler, priority);
        }

        public void fire(boolean success) {
            Entry<Handler>[] entries = entries();
            if (entries.length == 0) return;
            if (!hasLegacy()) {
                for (Entry<Handler> e : entries) {
                    try {
                        e.handler.onReload(success);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                return;
            }
            VoidReloadEvent pooled = legacyPool.get();
            pooled.init(success);
            for (Entry<Handler> e : entries) {
                try {
                    if (e.legacyListener != null) {
                        e.<VoidReloadEvent>legacyListenerAs().handle(pooled);
                    } else {
                        e.handler.onReload(success);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        protected boolean dispatchTypedFromLegacy(@NotNull VoidReloadEvent event, @NotNull Handler handler, boolean cancelled) {
            handler.onReload(event.isSuccess());
            return false;
        }

        @ApiStatus.Internal
        public static @NotNull Handler bridgeFromAny(@NotNull ac.voidac.api.event.VoidEvent.Handler abstractHandler) {
            return success -> abstractHandler.onAnyEvent(VoidReloadEvent.class, false);
        }
    }
}
