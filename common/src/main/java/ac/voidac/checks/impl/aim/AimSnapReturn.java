package ac.voidac.checks.impl.aim;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.impl.combat.CombatTracker;
import ac.voidac.checks.type.RotationCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.RotationUpdate;
import ac.voidac.utils.combat.SnapReturnDetector;

@CheckData(
        name = "AimSnapReturn",
        stableKey = "void.aim.snap_return",
        description = "Snapped to a target and returned to the previous rotation around an attack",
        killauraDedicated = true,
        decay = 0.05
)
public class AimSnapReturn extends Check implements RotationCheck {
    private SnapReturnDetector detector = new SnapReturnDetector(12, 2, 6);
    private long firstHitTick;
    private int hits;

    private int minHits;
    private int windowTicks;
    private int combatTicks;

    public AimSnapReturn(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minHits = config.getIntElse(getConfigName() + ".min-hits", 3);
        windowTicks = config.getIntElse(getConfigName() + ".window-ticks", 200);
        combatTicks = config.getIntElse(getConfigName() + ".combat-ticks", 1);
        detector = new SnapReturnDetector(
                config.getDoubleElse(getConfigName() + ".min-snap", 12),
                config.getDoubleElse(getConfigName() + ".max-residual", 2),
                config.getIntElse(getConfigName() + ".max-gap-ticks", 6));
    }

    @Override
    public void process(final RotationUpdate update) {
        if (!isFeatureEnabled() || !AimExemptions.applies(player)) {
            detector.reset();
            hits = 0;
            return;
        }

        CombatTracker tracker = player.checkManager.getPacketCheck(CombatTracker.class);
        long tick = tracker.currentTick();
        boolean combat = tracker.ticksSinceAttack() <= combatTicks;

        if (!detector.accept(tick, update.getDeltaXRot(), update.getDeltaYRot(), combat)) {
            if (combat) reward();
            return;
        }

        if (hits == 0 || tick - firstHitTick > windowTicks) {
            hits = 0;
            firstHitTick = tick;
        }

        if (++hits >= minHits) {
            flagAndAlert("hits=" + hits + ", span=" + (tick - firstHitTick) + " ticks");
            hits = 0;
        }
    }
}
