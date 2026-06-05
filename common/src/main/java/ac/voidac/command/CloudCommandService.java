package ac.voidac.command;

import ac.voidac.command.commands.*;
import ac.voidac.command.commands.VoidBanWave;
import ac.voidac.command.commands.VoidPunish;
import ac.voidac.command.handler.VoidCommandFailureHandler;
import ac.voidac.platform.api.command.CommandService;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.processors.requirements.RequirementApplicable;
import org.incendo.cloud.processors.requirements.RequirementApplicable.RequirementApplicableFactory;
import org.incendo.cloud.processors.requirements.RequirementPostprocessor;
import org.incendo.cloud.processors.requirements.Requirements;

import java.util.function.Function;
import java.util.function.Supplier;

public class CloudCommandService implements CommandService {

    public static final CloudKey<Requirements<Sender, SenderRequirement>> REQUIREMENT_KEY
            = CloudKey.of("requirements", new TypeToken<>() {});

    public static final RequirementApplicableFactory<Sender, SenderRequirement> REQUIREMENT_FACTORY
            = RequirementApplicable.factory(REQUIREMENT_KEY);

    private boolean commandsRegistered = false;

    private final Supplier<CommandManager<Sender>> commandManagerSupplier;
    private final CloudCommandAdapter commandAdapter;

    public CloudCommandService(Supplier<CommandManager<Sender>> commandManagerSupplier, CloudCommandAdapter commandAdapter) {
        this.commandManagerSupplier = commandManagerSupplier;
        this.commandAdapter = commandAdapter;
    }

    public void registerCommands() {
        if (commandsRegistered) return;
        CommandManager<Sender> commandManager = commandManagerSupplier.get();
        new VoidPerf().register(commandManager, commandAdapter);
        new VoidDebug().register(commandManager, commandAdapter);
        new VoidAlerts().register(commandManager, commandAdapter);
        new VoidProfile().register(commandManager, commandAdapter);
        new VoidSendAlert().register(commandManager, commandAdapter);
        new VoidHelp().register(commandManager, commandAdapter);
        new Blackowlzz().register(commandManager, commandAdapter);
        new VoidHistory().register(commandManager, commandAdapter);
        new VoidHistoryMigrate().register(commandManager, commandAdapter);
        new VoidHistoryCopy().register(commandManager, commandAdapter);
        new VoidReload().register(commandManager, commandAdapter);
        new VoidSpectate().register(commandManager, commandAdapter);
        new VoidStopSpectating().register(commandManager, commandAdapter);
        new VoidLog().register(commandManager, commandAdapter);
        new VoidVerbose().register(commandManager, commandAdapter);
        new VoidVersion().register(commandManager, commandAdapter);
        new VoidDump().register(commandManager, commandAdapter);
        new VoidBrands().register(commandManager, commandAdapter);
        new VoidList().register(commandManager, commandAdapter);
        new VoidTestWebhook().register(commandManager, commandAdapter);
        new VoidPunish().register(commandManager, commandAdapter);
        new VoidUnban().register(commandManager, commandAdapter);
        new VoidBanWave().register(commandManager, commandAdapter);
        new VoidPunishments().register(commandManager, commandAdapter);
        new VoidOptimizer().register(commandManager, commandAdapter);

        final RequirementPostprocessor<Sender, SenderRequirement>
                senderRequirementPostprocessor = RequirementPostprocessor.of(
                REQUIREMENT_KEY,
                new VoidCommandFailureHandler()
        );
        commandManager.registerCommandPostProcessor(senderRequirementPostprocessor);
        registerExceptionHandler(commandManager, InvalidSyntaxException.class, e ->
                MessageUtil.miniMessage("&8[&5Void&8] &7Usage: &f" + e.correctSyntax()));
        commandsRegistered = true;
    }

    protected <E extends Exception> void registerExceptionHandler(CommandManager<Sender> commandManager, Class<E> ex, Function<E, ComponentLike> toComponent) {
        commandManager.exceptionController().registerHandler(ex,
                (c) -> c.context().sender().sendMessage(toComponent.apply(c.exception()).asComponent().colorIfAbsent(NamedTextColor.RED))
        );
    }
}
