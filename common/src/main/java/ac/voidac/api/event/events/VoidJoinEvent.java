package ac.voidac.api.event.events;

import ac.voidac.api.VoidUser;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.event.VoidEvent;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class VoidJoinEvent extends VoidEvent<VoidJoinEvent.Channel> implements VoidUserEvent {
    private VoidUser user;

    /** Pool constructor: fields populated via {@link #init}. */
    public VoidJoinEvent() {
        super(true); // Async
    }

    public VoidJoinEvent(VoidUser user) {
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
        void onJoin(@NotNull VoidUser user);
    }

    public static final class Channel extends EventChannel<VoidJoinEvent, Handler> {
        private final ThreadLocal<VoidJoinEvent> legacyPool = ThreadLocal.withInitial(VoidJoinEvent::new);

        public Channel() {
            super(VoidJoinEvent.class, Handler.class);
        }

        public void onJoin(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribe(handler, 0, false, plugin, null);
        }

        public void onJoin(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribe(handler, priority, false, plugin, null);
        }

        /** @deprecated resolve your context once at plugin enable, {@code api.getVoidPlugin(this)}, and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onJoin(@NotNull Object pluginContext, @NotNull Handler handler) {
            onJoin(resolvePlugin(pluginContext), handler);
        }

        /** @deprecated see {@link #onJoin(Object, Handler)}. */
        @Deprecated
        public void onJoin(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            onJoin(resolvePlugin(pluginContext), handler, priority);
        }

        public void fire(@NotNull VoidUser user) {
            Entry<Handler>[] entries = entries();
            if (entries.length == 0) return;
            if (!hasLegacy()) {
                for (Entry<Handler> e : entries) {
                    try {
                        e.handler.onJoin(user);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                return;
            }
            VoidJoinEvent pooled = legacyPool.get();
            pooled.init(user);
            for (Entry<Handler> e : entries) {
                try {
                    if (e.legacyListener != null) {
                        e.<VoidJoinEvent>legacyListenerAs().handle(pooled);
                    } else {
                        e.handler.onJoin(user);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        protected boolean dispatchTypedFromLegacy(@NotNull VoidJoinEvent event, @NotNull Handler handler, boolean cancelled) {
            handler.onJoin(event.getUser());
            return false;
        }

        @ApiStatus.Internal
        public static @NotNull Handler bridgeFromAny(@NotNull ac.voidac.api.event.VoidEvent.Handler abstractHandler) {
            return user -> abstractHandler.onAnyEvent(VoidJoinEvent.class, false);
        }
    }
}
