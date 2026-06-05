package ac.voidac.api.event.events;

import ac.voidac.api.AbstractCheck;
import ac.voidac.api.VoidUser;
import ac.voidac.api.event.AbstractEventChannel;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.plugin.VoidPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class VoidVerboseCheckEvent<CHANNEL extends EventChannel<?, ?>>
        extends VoidCheckEvent<CHANNEL> {
    private String verbose;

    /** Pool constructor — fields populated via {@link #init(VoidUser, AbstractCheck, String)}. */
    protected VoidVerboseCheckEvent() {
        super();
    }

    public VoidVerboseCheckEvent(VoidUser user, AbstractCheck check, String verbose) {
        super(user, check);
        this.verbose = verbose;
    }

    @ApiStatus.Internal
    protected void init(VoidUser user, AbstractCheck check, String verbose) {
        super.init(user, check);
        this.verbose = verbose;
    }

    public String getVerbose() {
        return verbose;
    }

    /**
     * Abstract-level verbose-check handler. Fires for every concrete
     * {@code VoidVerboseCheckEvent} subtype — FlagEvent and
     * CommandExecuteEvent out of the box, plus any addon subtypes that
     * opt into bridging. Does not fire for
     * {@link CompletePredictionEvent}, which extends {@link VoidCheckEvent}
     * directly and has no verbose field.
     */
    @FunctionalInterface
    public interface Handler {
        boolean onVerboseCheck(@NotNull VoidUser user, @NotNull AbstractCheck check,
                               @NotNull String verbose, boolean currentlyCancelled);
    }

    public static final class Channel extends AbstractEventChannel<VoidVerboseCheckEvent<?>, Handler> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Channel() {
            super((Class<VoidVerboseCheckEvent<?>>) (Class) VoidVerboseCheckEvent.class, Handler.class);
        }

        public void onVerboseCheck(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribeAbstract(handler, 0, false, plugin);
        }

        public void onVerboseCheck(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribeAbstract(handler, priority, false, plugin);
        }

        public void onVerboseCheck(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority, boolean ignoreCancelled) {
            subscribeAbstract(handler, priority, ignoreCancelled, plugin);
        }

        /** @deprecated resolve your context once at plugin enable — {@code api.getVoidPlugin(this)} — and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onVerboseCheck(@NotNull Object pluginContext, @NotNull Handler handler) {
            subscribeAbstractResolving(pluginContext, handler, 0, false);
        }

        /** @deprecated see {@link #onVerboseCheck(Object, Handler)}. */
        @Deprecated
        public void onVerboseCheck(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            subscribeAbstractResolving(pluginContext, handler, priority, false);
        }

        /** @deprecated see {@link #onVerboseCheck(Object, Handler)}. */
        @Deprecated
        public void onVerboseCheck(@NotNull Object pluginContext, @NotNull Handler handler, int priority, boolean ignoreCancelled) {
            subscribeAbstractResolving(pluginContext, handler, priority, ignoreCancelled);
        }
    }
}
