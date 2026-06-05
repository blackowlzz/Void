package ac.voidac.api.events;

import ac.voidac.api.AbstractCheck;
import ac.voidac.api.VoidUser;
import lombok.Getter;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Deprecated(since = "1.2.1.0", forRemoval = true)
public class CompletePredictionEvent extends FlagEvent {

    private static final HandlerList handlers = new HandlerList();
    @Getter private final double offset;
    private boolean cancelled;

    public CompletePredictionEvent(VoidUser player, AbstractCheck check, String verbose, double offset) {
        super(player, check, verbose);
        this.offset = offset;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

}
