package ac.voidac.api.event.events;

import ac.voidac.api.AbstractCheck;
import ac.voidac.api.VoidUser;
import ac.voidac.api.event.AbstractEventChannel;
import ac.voidac.api.event.Cancellable;
import ac.voidac.api.event.EventChannel;
import ac.voidac.api.event.VoidEvent;
import ac.voidac.api.plugin.VoidPlugin;
import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class VoidCheckEvent<CHANNEL extends EventChannel<?, ?>>
        extends VoidEvent<CHANNEL> implements VoidUserEvent, Cancellable {
    private VoidUser user;
    @Getter
    protected AbstractCheck check;
    private boolean cancelled;

    /** Pool constructor: fields populated via {@link #init(VoidUser, AbstractCheck)}. */
    protected VoidCheckEvent() {
        super(true); // Async
    }

    public VoidCheckEvent(VoidUser user, AbstractCheck check) {
        super(true); // Async
        this.user = user;
        this.check = check;
    }

    @ApiStatus.Internal
    protected void init(VoidUser user, AbstractCheck check) {
        resetForReuse();
        this.user = user;
        this.check = check;
        this.cancelled = false;
    }

    @Override
    public VoidUser getUser() {
        return user;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancellable() {
        return true;
    }

    public double getViolations() {
        return check.getViolations();
    }

    public boolean isSetback() {
        return check.getViolations() > check.getSetbackVL();
    }

    /**
     * Abstract-level check handler. Fires for every concrete
     * {@code VoidCheckEvent} subtype (FlagEvent, CompletePredictionEvent,
     * CommandExecuteEvent, and any addon subtypes that opt into bridging).
     *
     * <p>Returns the new cancelled state, the value is threaded back into
     * the priority-ordered dispatch loop of whichever concrete subtype
     * fired, so a high-priority abstract subscriber can cancel and
     * lower-priority direct subscribers to the concrete event see the
     * cancellation just like any other priority-ordered handler.
     */
    @FunctionalInterface
    public interface Handler {
        boolean onCheck(@NotNull VoidUser user, @NotNull AbstractCheck check, boolean currentlyCancelled);
    }

    public static final class Channel extends AbstractEventChannel<VoidCheckEvent<?>, Handler> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Channel() {
            super((Class<VoidCheckEvent<?>>) (Class) VoidCheckEvent.class, Handler.class);
        }

        public void onCheck(@NotNull VoidPlugin plugin, @NotNull Handler handler) {
            subscribeAbstract(handler, 0, false, plugin);
        }

        public void onCheck(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority) {
            subscribeAbstract(handler, priority, false, plugin);
        }

        public void onCheck(@NotNull VoidPlugin plugin, @NotNull Handler handler, int priority, boolean ignoreCancelled) {
            subscribeAbstract(handler, priority, ignoreCancelled, plugin);
        }

        /** @deprecated resolve your context once at plugin enable, {@code api.getVoidPlugin(this)}, and call the {@link VoidPlugin}-taking overload. */
        @Deprecated
        public void onCheck(@NotNull Object pluginContext, @NotNull Handler handler) {
            subscribeAbstractResolving(pluginContext, handler, 0, false);
        }

        /** @deprecated see {@link #onCheck(Object, Handler)}. */
        @Deprecated
        public void onCheck(@NotNull Object pluginContext, @NotNull Handler handler, int priority) {
            subscribeAbstractResolving(pluginContext, handler, priority, false);
        }

        /** @deprecated see {@link #onCheck(Object, Handler)}. */
        @Deprecated
        public void onCheck(@NotNull Object pluginContext, @NotNull Handler handler, int priority, boolean ignoreCancelled) {
            subscribeAbstractResolving(pluginContext, handler, priority, ignoreCancelled);
        }
    }
}
