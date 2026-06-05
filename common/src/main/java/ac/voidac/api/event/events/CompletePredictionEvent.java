package ac.voidac.api.event.events;

import ac.voidac.api.AbstractCheck;
import ac.voidac.api.VoidUser;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class CompletePredictionEvent extends VoidCheckEvent<CompletePredictionEvent.Channel> {
    private double offset;

    /** Pool constructor — fields populated via {@link #init}. */
    public CompletePredictionEvent() {
        super();
    }

    public CompletePredictionEvent(VoidUser player, AbstractCheck check, double offset) {
        super(player, check);
        this.offset = offset;
    }

    @ApiStatus.Internal
    public void init(VoidUser user, AbstractCheck check, double offset) {
        super.init(user, check);
        this.offset = offset;
    }

    public double getOffset() {
        return offset;
    }

    /**
     * Typed prediction-complete handler. Returns the new cancelled state — see
     * {@link FlagEvent.Handler} for the cancellation contract.
     */
    @FunctionalInterface
    public interface Handler {
        boolean onCompletePrediction(@NotNull VoidUser user, @NotNull AbstractCheck check,
                                     double offset, boolean currentlyCancelled);
    }

    public static final class Channel extends EventChannel<CompletePredictionEvent, Handler> {
        private final ThreadLocal<CompletePredictionEvent> legacyPool =
                ThreadLocal.withInitial(CompletePredictionEvent::new);

        public Channel() {
            super(CompletePredictionEvent.class, Handler.class);
        }

        public void onCompletePrediction(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribe(handler, 0, false, plugin, null);
        }

        public void onCompletePrediction(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribe(handler, priority, false, plugin, null);
        }

        public void onCompletePrediction(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority, boolean ignoreCancelled) {
            subscribe(handler, priority, ignoreCancelled, plugin, null);
        }

        /** @deprecated resolve your context once at plugin enable — {@code api.getVoidPlugin(this)} — and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onCompletePrediction(@NotNull Object pluginContext, @NotNull Handler handler) {
            onCompletePrediction(resolvePlugin(pluginContext), handler);
        }

        /** @deprecated see {@link #onCompletePrediction(Object, Handler)}. */
        @Deprecated
        public void onCompletePrediction(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            onCompletePrediction(resolvePlugin(pluginContext), handler, priority);
        }

        /** @deprecated see {@link #onCompletePrediction(Object, Handler)}. */
        @Deprecated
        public void onCompletePrediction(@NotNull Object pluginContext, @NotNull Handler handler, int priority, boolean ignoreCancelled) {
            onCompletePrediction(resolvePlugin(pluginContext), handler, priority, ignoreCancelled);
        }

        public boolean fire(@NotNull VoidUser user, @NotNull AbstractCheck check, double offset) {
            Entry<Handler>[] entries = entries();
            if (entries.length == 0) return false;

            boolean cancelled = false;
            if (!hasLegacy()) {
                for (Entry<Handler> e : entries) {
                    if (cancelled && !e.ignoreCancelled) continue;
                    try {
                        cancelled = e.handler.onCompletePrediction(user, check, offset, cancelled);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                return cancelled;
            }

            CompletePredictionEvent pooled = legacyPool.get();
            pooled.init(user, check, offset);
            for (Entry<Handler> e : entries) {
                if (cancelled && !e.ignoreCancelled) continue;
                try {
                    if (e.legacyListener != null) {
                        pooled.setCancelled(cancelled);
                        e.<CompletePredictionEvent>legacyListenerAs().handle(pooled);
                        cancelled = pooled.isCancelled();
                    } else {
                        cancelled = e.handler.onCompletePrediction(user, check, offset, cancelled);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            return cancelled;
        }

        @Override
        protected boolean dispatchTypedFromLegacy(@NotNull CompletePredictionEvent event, @NotNull Handler handler, boolean cancelled) {
            return handler.onCompletePrediction(event.getUser(), event.getCheck(), event.getOffset(), cancelled);
        }

        @ApiStatus.Internal
        public static @NotNull Handler bridgeFromCheck(@NotNull VoidCheckEvent.Handler abstractHandler) {
            return (user, check, offset, cancelled) -> abstractHandler.onCheck(user, check, cancelled);
        }

        @ApiStatus.Internal
        public static @NotNull Handler bridgeFromAny(@NotNull ac.voidac.api.event.VoidEvent.Handler abstractHandler) {
            return (user, check, offset, cancelled) -> {
                abstractHandler.onAnyEvent(CompletePredictionEvent.class, cancelled);
                return cancelled;
            };
        }
    }
}
