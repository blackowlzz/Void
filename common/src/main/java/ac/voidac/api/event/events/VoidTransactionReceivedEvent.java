package ac.voidac.api.event.events;

import ac.voidac.api.VoidUser;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.event.VoidEvent;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when Void receives an inbound response for a transaction packet that
 * it previously sent.
 *
 * <p>Void cancels these inbound packets by default, controlled by the
 * {@code disable-pong-cancelling} option in {@code config.yml}. The
 * {@code packetCancelled} parameter reflects whether Void cancelled packet
 * handling; it is not an event-cancellation flag (this event is observational
 * and not cancellable).
 *
 * <p>Only fires for transactions initiated by Void, on the Netty thread
 * associated with the user.
 */
public final class VoidTransactionReceivedEvent extends VoidEvent<VoidTransactionReceivedEvent.Channel> {
    private VoidTransactionReceivedEvent() {
        // Never instantiated: exists only as a Class key for bus.get(VoidTransactionReceivedEvent.class).
    }

    @FunctionalInterface
    public interface Handler {
        void onTransactionReceived(@NotNull VoidUser user, int transactionId, boolean packetCancelled, long timestamp);
    }

    public static final class Channel extends EventChannel<VoidTransactionReceivedEvent, Handler> {
        public Channel() {
            super(VoidTransactionReceivedEvent.class, Handler.class);
        }

        public void onTransactionReceived(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribe(handler, 0, false, plugin, null);
        }

        public void onTransactionReceived(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribe(handler, priority, false, plugin, null);
        }

        /** @deprecated resolve your context once at plugin enable, {@code api.getVoidPlugin(this)}, and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onTransactionReceived(@NotNull Object pluginContext, @NotNull Handler handler) {
            onTransactionReceived(resolvePlugin(pluginContext), handler);
        }

        /** @deprecated see {@link #onTransactionReceived(Object, Handler)}. */
        @Deprecated
        public void onTransactionReceived(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            onTransactionReceived(resolvePlugin(pluginContext), handler, priority);
        }

        public void fire(@NotNull VoidUser user, int transactionId, boolean packetCancelled, long timestamp) {
            Entry<Handler>[] entries = entries();
            for (Entry<Handler> e : entries) {
                try {
                    e.handler.onTransactionReceived(user, transactionId, packetCancelled, timestamp);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        protected boolean dispatchTypedFromLegacy(@NotNull VoidTransactionReceivedEvent event, @NotNull Handler handler, boolean cancelled) {
            throw new UnsupportedOperationException("VoidTransactionReceivedEvent has no legacy representation");
        }

        @org.jetbrains.annotations.ApiStatus.Internal
        public static @NotNull Handler bridgeFromAny(@NotNull ac.voidac.api.event.VoidEvent.Handler abstractHandler) {
            return (user, id, c, ts) -> abstractHandler.onAnyEvent(VoidTransactionReceivedEvent.class, false);
        }
    }
}
