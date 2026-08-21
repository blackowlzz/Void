package ac.voidac.api.event.events;

import ac.voidac.api.VoidUser;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.event.VoidEvent;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class VoidQuitEvent extends VoidEvent<VoidQuitEvent.Channel> implements VoidUserEvent {
    private VoidUser user;

    /** Pool constructor: fields populated via {@link #init}. */
    public VoidQuitEvent() {
        super(true); // Async
    }

    public VoidQuitEvent(VoidUser user) {
        super(true); // Async
        this.user = user;
    }

    @ApiStatus.Internal
    public void init(VoidUser user) {
        resetForReuse();
        this.user = user;
    }

    @Override
    public VoidUser getUser() {
        return user;
    }

    @FunctionalInterface
    public interface Handler {
        void onQuit(@NotNull VoidUser user);
    }

    public static final class Channel extends EventChannel<VoidQuitEvent, Handler> {
        private final ThreadLocal<VoidQuitEvent> legacyPool = ThreadLocal.withInitial(VoidQuitEvent::new);

        public Channel() {
            super(VoidQuitEvent.class, Handler.class);
        }

        public void onQuit(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribe(handler, 0, false, plugin, null);
        }

        public void onQuit(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribe(handler, priority, false, plugin, null);
        }

        /** @deprecated resolve your context once at plugin enable, {@code api.getVoidPlugin(this)}, and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onQuit(@NotNull Object pluginContext, @NotNull Handler handler) {
            onQuit(resolvePlugin(pluginContext), handler);
        }

        /** @deprecated see {@link #onQuit(Object, Handler)}. */
        @Deprecated
        public void onQuit(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            onQuit(resolvePlugin(pluginContext), handler, priority);
        }

        public void fire(@NotNull VoidUser user) {
            Entry<Handler>[] entries = entries();
            if (entries.length == 0) return;
            if (!hasLegacy()) {
                for (Entry<Handler> e : entries) {
                    try {
                        e.handler.onQuit(user);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                return;
            }
            VoidQuitEvent pooled = legacyPool.get();
            pooled.init(user);
            for (Entry<Handler> e : entries) {
                try {
                    if (e.legacyListener != null) {
                        e.<VoidQuitEvent>legacyListenerAs().handle(pooled);
                    } else {
                        e.handler.onQuit(user);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        protected boolean dispatchTypedFromLegacy(@NotNull VoidQuitEvent event, @NotNull Handler handler, boolean cancelled) {
            handler.onQuit(event.getUser());
            return false;
        }

        @ApiStatus.Internal
        public static @NotNull Handler bridgeFromAny(@NotNull ac.voidac.api.event.VoidEvent.Handler abstractHandler) {
            return user -> abstractHandler.onAnyEvent(VoidQuitEvent.class, false);
        }
    }
}
