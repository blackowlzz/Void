package ac.voidac.manager.init.start;

import ac.voidac.platform.api.command.CommandService;
import ac.voidac.utils.anticheat.LogUtil;

public record CommandRegister(CommandService service) implements StartableInitable {

    @Override
    public void start() {
        try {
            if (service != null) {
                service.registerCommands();
            }
        } catch (Throwable t) {
            // This is the ultimate safety net. If command registration fails, Void keeps running.
            LogUtil.error("Failed to register commands! Void will run without command support.", t);
        }
    }
}
