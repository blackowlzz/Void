package ac.voidac.checks.debug;

import ac.voidac.checks.Check;
import ac.voidac.player.VoidPlayer;

public abstract class AbstractDebugHandler extends Check {
    public AbstractDebugHandler(VoidPlayer player) {
        super(player);
    }

    public abstract void toggleListener(VoidPlayer player);

    public abstract boolean toggleConsoleOutput();
}
