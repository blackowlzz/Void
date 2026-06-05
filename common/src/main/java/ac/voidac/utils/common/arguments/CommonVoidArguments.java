package ac.voidac.utils.common.arguments;

import ac.voidac.platform.api.Platform;

import static ac.voidac.utils.common.arguments.ArgumentUtils.*;

public class CommonVoidArguments {

    private final static SystemArgumentFactory FACTORY = SystemArgumentFactory.Builder.of("Void")
            .optionModifier(builder -> builder.key("Void" + builder.options().getKey()))
            .supportEnv()
            .build();

    public final static SystemArgument<Boolean> KICK_ON_TRANSACTION_ERRORS = FACTORY.create(string("KickOnTransactionTaskErrors", false));
    public final static SystemArgument<Platform> PLATFORM_OVERRIDE = FACTORY.create(platform("PlatformOverride"));
    public final static SystemArgument<Integer> URL_TIMEOUT = FACTORY.create(range("UrlTimeout", 10000, 1000, 60000));

    /**
     * Enables "Fast Bypass" mode for chat messages sent by Void.
     * <p>
     * <b>BENEFIT:</b> Messages are sent directly as packets, significantly improving
     * performance and reducing server overhead especially when lots of alerts are being sent.
     * <p>
     * <b>TRADE-OFF:</b> This completely bypasses the platform's event system (e.g., Bukkit's chat events).
     * Other plugins will NOT be able to see, format, or cancel these messages.
     * <p>
     * This setting is opt-in (default: false) and requires a server restart to change.
     */
    public final static SystemArgument<Boolean> USE_CHAT_FAST_BYPASS = FACTORY.create(string("ChatFastBypass", true));

    /**
     * If true, players will be kicked when they try to connect from a proxy server with ViaVersion installed.
     * <p>
     * This setting is opt-out (default: true) and requires a server restart to change.
     */
    public final static SystemArgument<Boolean> KICK_ON_VIA_PROXY = FACTORY.create(string("KickOnViaProxy", true));


}
