package ac.voidac.api.event.events;

import ac.voidac.api.VoidUser;

public interface VoidUserEvent {
    VoidUser getUser();
    default VoidUser getPlayer() {
        return getUser();
    }
}
