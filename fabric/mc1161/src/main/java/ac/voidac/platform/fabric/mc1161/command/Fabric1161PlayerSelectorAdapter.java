package ac.voidac.platform.fabric.mc1161.command;

import ac.voidac.VoidAPI;
import ac.voidac.platform.api.command.PlayerSelector;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.platform.fabric.sender.FabricSenderFactory;
import org.incendo.cloud.minecraft.modded.data.SinglePlayerSelector;

import java.util.Collection;
import java.util.Collections;

public class Fabric1161PlayerSelectorAdapter implements PlayerSelector {
    protected final SinglePlayerSelector fabricSelector;

    public Fabric1161PlayerSelectorAdapter(SinglePlayerSelector fabricSelector) {
        this.fabricSelector = fabricSelector;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Sender getSinglePlayer() {
        return ((FabricSenderFactory) VoidAPI.INSTANCE.getSenderFactory()).wrap(fabricSelector.single().createCommandSourceStack());
    }

    @Override
    public Collection<Sender> getPlayers() {
        return Collections.singletonList(getSinglePlayer()); // Assuming your ServerPlayer can be cast to Player
    }

    @Override
    public String inputString() {
        return fabricSelector.inputString();
    }
}
