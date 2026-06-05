package ac.voidac.events.packets;

import ac.voidac.VoidAPI;
import ac.voidac.api.event.events.VoidTransactionReceivedEvent;
import ac.voidac.api.event.events.VoidTransactionSendEvent;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.data.Pair;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;

public class PacketPingListener extends PacketListenerAbstract {

    private static final VoidTransactionSendEvent.Channel SEND_CHANNEL = VoidAPI.INSTANCE.getEventBus().get(VoidTransactionSendEvent.class);
    private static final VoidTransactionReceivedEvent.Channel RECEIVED_CHANNEL = VoidAPI.INSTANCE.getEventBus().get(VoidTransactionReceivedEvent.class);

    // Must listen on LOWEST (or maybe low) to stop Tuinity packet limiter from kicking players for transaction/pong spam
    public PacketPingListener() {
        super(PacketListenerPriority.LOWEST);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION) {
            WrapperPlayClientWindowConfirmation transaction = new WrapperPlayClientWindowConfirmation(event);
            short id = transaction.getActionId();

            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;
            player.packetStateData.lastTransactionPacketWasValid = false;

            // Vanilla always uses an ID starting from 1
            // Check if we sent this packet before cancelling it
            if (id <= 0 && player.addTransactionResponse(id)) {
                player.packetStateData.lastTransactionPacketWasValid = true;
                boolean shouldCancel = !VoidAPI.INSTANCE.getConfigManager().isDisablePongCancelling();
                // Not needed for vanilla as vanilla ignores this packet, needed for packet limiters
                event.setCancelled(shouldCancel);
                RECEIVED_CHANNEL.fire(player, id, shouldCancel, event.getTimestamp());
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.PONG) {
            WrapperPlayClientPong pong = new WrapperPlayClientPong(event);
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;
            player.packetStateData.lastTransactionPacketWasValid = false;

            int id = pong.getId();
            // If it wasn't below 0, it wasn't us
            // If it wasn't in short range, it wasn't us either
            if (id == (short) id) {
                short shortID = ((short) id);
                if (player.addTransactionResponse(shortID)) {
                    player.packetStateData.lastTransactionPacketWasValid = true;
                    boolean shouldCancel = !VoidAPI.INSTANCE.getConfigManager().isDisablePongCancelling();
                    // Not needed for vanilla as vanilla ignores this packet, needed for packet limiters
                    event.setCancelled(shouldCancel);
                    RECEIVED_CHANNEL.fire(player, id, shouldCancel, event.getTimestamp());
                }
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.WINDOW_CONFIRMATION) {
            WrapperPlayServerWindowConfirmation confirmation = new WrapperPlayServerWindowConfirmation(event);
            short id = confirmation.getActionId();
            //
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;
            player.packetStateData.lastServerTransWasValid = false;
            // Vanilla always uses an ID starting from 1
            if (id <= 0) {
                if (player.didWeSendThatTrans.remove(id)) {
                    player.packetStateData.lastServerTransWasValid = true;
                    player.transactionsSent.add(new Pair<>(id, System.nanoTime()));
                    player.lastTransactionSent.getAndIncrement();
                    SEND_CHANNEL.fire(player, id, event.getTimestamp());
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Server.PING) {
            WrapperPlayServerPing pong = new WrapperPlayServerPing(event);
            int id = pong.getId();
            //
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;
            player.packetStateData.lastServerTransWasValid = false;
            // Check if in the short range, we only use short range
            if (id == (short) id) {
                // Cast ID twice so we can use the list
                Short shortID = ((short) id);
                if (player.didWeSendThatTrans.remove(shortID)) {
                    player.packetStateData.lastServerTransWasValid = true;
                    player.transactionsSent.add(new Pair<>(shortID, System.nanoTime()));
                    player.lastTransactionSent.getAndIncrement();
                    SEND_CHANNEL.fire(player, id, event.getTimestamp());
                }
            }
        }
    }
}
