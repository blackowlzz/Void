package ac.voidac.checks.impl.misc;

import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.player.VoidPlayer;

@CheckData(name = "TransactionOrder", stableKey = "void.ping.invalid_transaction_order")
public class TransactionOrder extends Check {
    public TransactionOrder(VoidPlayer player) {
        super(player);
    }
}
