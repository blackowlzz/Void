package ac.voidac.api.event.events;

import ac.voidac.api.VoidUser;
import ac.voidac.api.event.AbstractEventChannel;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.event.VoidEvent;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when Void executes a setback against a player — the semantic
 * anticheat action, independent of the specific packet type used.
 *
 * <p>Dispatches through two concrete subtypes:
 * <ul>
 *   <li>{@link VoidPlayerSetbackEvent} — player-on-foot setback, sent as a
 *       {@code ServerPlayerPositionAndLook} / teleport packet.</li>
 *   <li>{@link VoidVehicleSetbackEvent} — player-in-vehicle setback, sent as
 *       a {@code ServerVehicleMove} packet.</li>
 * </ul>
 *
 * <p>Abstract-level subscribers receive a bridged dispatch for every fire
 * of either concrete child — the hot path goes through the child's channel
 * with no extra indirection. Observational, not cancellable.
 *
 * <p>Fires on the Netty thread associated with the user.
 */
public abstract class VoidSetbackEvent<CHANNEL extends EventChannel<?, ?>> extends VoidEvent<CHANNEL> {
    protected VoidSetbackEvent() {
        super(true); // Async — setbacks are sent from the netty thread
    }

    /**
     * Abstract-level setback handler. Fires for every concrete
     * {@code VoidSetbackEvent} subtype (player + vehicle) and any addon
     * subtypes that opt into bridging.
     *
     * <p>Carries only the fields both children share: the user and the
     * timestamp at which the setback packet was emitted. Subscribers that
     * need the destination position, teleport id, or packet type should
     * subscribe at the concrete child level.
     */
    @FunctionalInterface
    public interface Handler {
        void onAnySetback(@NotNull VoidUser user, long timestamp);
    }

    public static final class Channel extends AbstractEventChannel<VoidSetbackEvent<?>, Handler> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Channel() {
            super((Class<VoidSetbackEvent<?>>) (Class) VoidSetbackEvent.class, Handler.class);
        }

        public void onAnySetback(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribeAbstract(handler, 0, false, plugin);
        }

        public void onAnySetback(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribeAbstract(handler, priority, false, plugin);
        }

        /** @deprecated resolve your context once at plugin enable — {@code api.getVoidPlugin(this)} — and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onAnySetback(@NotNull Object pluginContext, @NotNull Handler handler) {
            subscribeAbstractResolving(pluginContext, handler, 0, false);
        }

        /** @deprecated see {@link #onAnySetback(Object, Handler)}. */
        @Deprecated
        public void onAnySetback(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            subscribeAbstractResolving(pluginContext, handler, priority, false);
        }
    }
}
