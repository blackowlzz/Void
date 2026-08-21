package ac.voidac.api.event.events;

import ac.voidac.api.VoidUser;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.event.VoidEvent;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when Void sends a teleport packet to the client.
 *
 * <p>Exists to help maintain compatibility with packet-based plugins and other
 * anticheats that track inbound/outbound teleport packets to build a
 * pending-teleport deque.
 *
 * <p>Fires on the Netty thread associated with the PacketEvents user.
 * Observational, not cancellable.
 */
public final class VoidTeleportEvent extends VoidEvent<VoidTeleportEvent.Channel> {
    private VoidTeleportEvent() {
        // Never instantiated: exists only as a Class key for bus.get(VoidTeleportEvent.class).
    }

    @FunctionalInterface
    public interface Handler {
        void onTeleport(@NotNull VoidUser user, int teleportId, long timestamp);
    }

    public static final class Channel extends EventChannel<VoidTeleportEvent, Handler> {
        public Channel() {
            super(VoidTeleportEvent.class, Handler.class);
        }

        public void onTeleport(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribe(handler, 0, false, plugin, null);
        }

        public void onTeleport(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribe(handler, priority, false, plugin, null);
        }

        /** @deprecated resolve your context once at plugin enable, {@code api.getVoidPlugin(this)}, and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onTeleport(@NotNull Object pluginContext, @NotNull Handler handler) {
            onTeleport(resolvePlugin(pluginContext), handler);
        }

        /** @deprecated see {@link #onTeleport(Object, Handler)}. */
        @Deprecated
        public void onTeleport(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            onTeleport(resolvePlugin(pluginContext), handler, priority);
        }

        public void fire(@NotNull VoidUser user, int teleportId, long timestamp) {
            Entry<Handler>[] entries = entries();
            for (Entry<Handler> e : entries) {
                try {
                    e.handler.onTeleport(user, teleportId, timestamp);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        protected boolean dispatchTypedFromLegacy(@NotNull VoidTeleportEvent event, @NotNull Handler handler, boolean cancelled) {
            // Unreachable: VoidTeleportEvent has no public constructor, so no caller can post() one.
            throw new UnsupportedOperationException("VoidTeleportEvent has no legacy representation");
        }

        @org.jetbrains.annotations.ApiStatus.Internal
        public static @NotNull Handler bridgeFromAny(@NotNull ac.voidac.api.event.VoidEvent.Handler abstractHandler) {
            return (user, id, ts) -> abstractHandler.onAnyEvent(VoidTeleportEvent.class, false);
        }
    }
}
