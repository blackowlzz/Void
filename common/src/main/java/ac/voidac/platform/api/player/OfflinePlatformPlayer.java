package ac.voidac.platform.api.player;

import ac.voidac.api.VoidIdentity;

public interface OfflinePlatformPlayer extends VoidIdentity {

    boolean isOnline();

    String getName();
}
