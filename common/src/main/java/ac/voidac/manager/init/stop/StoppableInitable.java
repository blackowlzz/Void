package ac.voidac.manager.init.stop;

import ac.voidac.manager.init.Initable;

public interface StoppableInitable extends Initable {
    void stop();
}
