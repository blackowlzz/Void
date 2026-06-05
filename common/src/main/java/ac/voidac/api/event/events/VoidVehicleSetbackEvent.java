package ac.voidac.api.event.events;

import ac.voidac.api.VoidUser;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when Void sends a player-in-vehicle setback — the
 * {@code ServerVehicleMove} packet branch of {@link VoidSetbackEvent}.
 *
 * <p>Unlike {@link VoidPlayerSetbackEvent}, vehicle-move packets carry no
 * teleport id, so there is nothing for a packet-tracking consumer to
 * correlate against an incoming confirm; this event exists primarily for
 * the semantic "Void did a setback" audience (admin tools, stats,
 * anticheat-test harnesses).
 *
 * <p>Fires on the Netty thread associated with the user. Observational,
 * not cancellable.
 */
public final class VoidVehicleSetbackEvent extends VoidSetbackEvent<VoidVehicleSetbackEvent.Channel> {
    private VoidVehicleSetbackEvent() {
        // Never instantiated — exists only as a Class key for bus.get(VoidVehicleSetbackEvent.class).
    }

    @FunctionalInterface
    public interface Handler {
        void onVehicleSetback(@NotNull VoidUser user,
                              double x, double y, double z, long timestamp);
    }

    public static final class Channel extends EventChannel<VoidVehicleSetbackEvent, Handler> {
        public Channel() {
            super(VoidVehicleSetbackEvent.class, Handler.class);
        }

        public void onVehicleSetback(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribe(handler, 0, false, plugin, null);
        }

        public void onVehicleSetback(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribe(handler, priority, false, plugin, null);
        }

        /** @deprecated resolve your context once at plugin enable — {@code api.getVoidPlugin(this)} — and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onVehicleSetback(@NotNull Object pluginContext, @NotNull Handler handler) {
            onVehicleSetback(resolvePlugin(pluginContext), handler);
        }

        /** @deprecated see {@link #onVehicleSetback(Object, Handler)}. */
        @Deprecated
        public void onVehicleSetback(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            onVehicleSetback(resolvePlugin(pluginContext), handler, priority);
        }

        public void fire(@NotNull VoidUser user,
                         double x, double y, double z, long timestamp) {
            Entry<Handler>[] entries = entries();
            for (Entry<Handler> e : entries) {
                try {
                    e.handler.onVehicleSetback(user, x, y, z, timestamp);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        protected boolean dispatchTypedFromLegacy(@NotNull VoidVehicleSetbackEvent event, @NotNull Handler handler, boolean cancelled) {
            // Unreachable — no public constructor, so no caller can post() one.
            throw new UnsupportedOperationException("VoidVehicleSetbackEvent has no legacy representation");
        }

        /** Bridge from {@link VoidSetbackEvent.Handler} — used by the abstract channel when a setback-level subscriber registers. */
        @ApiStatus.Internal
        public static @NotNull Handler bridgeFromSetback(@NotNull VoidSetbackEvent.Handler abstractHandler) {
            return (user, x, y, z, ts) -> abstractHandler.onAnySetback(user, ts);
        }

        /** Bridge from root-level {@link ac.voidac.api.event.VoidEvent.Handler}. */
        @ApiStatus.Internal
        public static @NotNull Handler bridgeFromAny(@NotNull ac.voidac.api.event.VoidEvent.Handler abstractHandler) {
            return (user, x, y, z, ts) -> abstractHandler.onAnyEvent(VoidVehicleSetbackEvent.class, false);
        }
    }
}
