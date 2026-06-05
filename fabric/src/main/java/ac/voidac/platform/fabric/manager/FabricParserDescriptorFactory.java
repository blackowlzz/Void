package ac.voidac.platform.fabric.manager;

import ac.voidac.platform.api.command.PlayerSelector;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.platform.fabric.VoidFabricLoaderPlugin;
import ac.voidac.platform.fabric.command.FabricPlayerSelectorParser;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.level.ServerPlayer;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class FabricParserDescriptorFactory implements CloudCommandAdapter {

    private final FabricPlayerSelectorParser<Sender> fabricPlayerSelectorParser;

    @Override
    public ParserDescriptor<Sender, PlayerSelector> singlePlayerSelectorParser() {
        return fabricPlayerSelectorParser.descriptor();
    }

    // TODO (Cross-platform) brigadier style & better suggestions
    @Override
    public SuggestionProvider<Sender> onlinePlayerSuggestions() {
        return (context, input) -> {
            List<Suggestion> suggestions = new ArrayList<>();

            // TODO Support Vanish mods?
            for (ServerPlayer player : VoidFabricLoaderPlugin.FABRIC_SERVER.getPlayerList().getPlayers()) {
                suggestions.add(Suggestion.suggestion(player.getName().getString()));
            }

            return CompletableFuture.completedFuture(suggestions);
        };
    }
}
