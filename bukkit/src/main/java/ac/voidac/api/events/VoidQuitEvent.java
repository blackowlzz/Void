package ac.voidac.api.events;

import ac.voidac.api.VoidUser;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Deprecated(since = "1.2.1.0", forRemoval = true)
public class VoidQuitEvent extends Event implements VoidUserEvent {

    private static final HandlerList handlers = new HandlerList();
    private final VoidUser user;

    public VoidQuitEvent(VoidUser user) {
        super(true); // Async!
        this.user = user;
    }

    @Override
    public VoidUser getUser() {
        return user;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
