package ac.voidac.api.events;

import ac.voidac.api.VoidUser;

@Deprecated(since = "1.2.1.0", forRemoval = true)
public interface VoidUserEvent {

    VoidUser getUser();

    default VoidUser getPlayer() {
        return getUser();
    }

}
