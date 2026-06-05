package ac.voidac.api.event.events;

import ac.voidac.api.VoidUser;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.event.VoidEvent;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when Void sends a transaction packet to the client.
 *
 * <p>Plugins can use this event to track transaction ids issued by Void and
 * correlate them with the matching {@link VoidTransactionReceivedEvent} once a
 * response is received.
 *
 * <p>Fires on the Netty thread associated with the user. Observational, not
 * cancellable.
 */
public final class VoidTransactionSendEvent extends VoidEvent<VoidTransactionSendEvent.Channel> {
    private VoidTransactionSendEvent() {
        // Never instantiated — exists only as a Class key for bus.get(VoidTransactionSendEvent.class).
    }

    @FunctionalInterface
    public interface Handler {
        void onTransactionSend(@NotNull VoidUser user, int transactionId, long timestamp);
    }

    public static final class Channel extends EventChannel<VoidTransactionSendEvent, Handler> {
        public Channel() {
            super(VoidTransactionSendEvent.class, Handler.class);
        }

        public void onTransactionSend(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribe(handler, 0, false, plugin, null);
        }

        public void onTransactionSend(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribe(handler, priority, false, plugin, null);
        }

        /** @deprecated resolve your context once at plugin enable — {@code api.getVoidPlugin(this)} — and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onTransactionSend(@NotNull Object pluginContext, @NotNull Handler handler) {
            onTransactionSend(resolvePlugin(pluginContext), handler);
        }

        /** @deprecated see {@link #onTransactionSend(Object, Handler)}. */
        @Deprecated
        public void onTransactionSend(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            onTransactionSend(resolvePlugin(pluginContext), handler, priority);
        }

        public void fire(@NotNull VoidUser user, int transactionId, long timestamp) {
            Entry<Handler>[] entries = entries();
            for (Entry<Handler> e : entries) {
                try {
                    e.handler.onTransactionSend(user, transactionId, timestamp);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        protected boolean dispatchTypedFromLegacy(@NotNull VoidTransactionSendEvent event, @NotNull Handler handler, boolean cancelled) {
            throw new UnsupportedOperationException("VoidTransactionSendEvent has no legacy representation");
        }

        @org.jetbrains.annotations.ApiStatus.Internal
        public static @NotNull Handler bridgeFromAny(@NotNull ac.voidac.api.event.VoidEvent.Handler abstractHandler) {
            return (user, id, ts) -> abstractHandler.onAnyEvent(VoidTransactionSendEvent.class, false);
        }
    }
}
