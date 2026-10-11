package ac.voidac.checks.impl.aim;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.impl.aim.processor.AimProcessor;
import ac.voidac.checks.impl.combat.CombatTracker;
import ac.voidac.checks.type.RotationCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.RotationUpdate;
import ac.voidac.utils.combat.BooleanWindow;
import ac.voidac.utils.combat.SensitivityGrid;
import ac.voidac.utils.combat.StepEstimator;

@CheckData(
        name = "AimGcd",
        stableKey = "void.aim.gcd",
        description = "Rotated off the mouse sensitivity grid while fighting",
        killauraDedicated = true,
        decay = 0.02
)
public class AimGcd extends Check implements RotationCheck {
    private static final int WINDOW = 16;
    private static final float MAX_DELTA = 180;
    private static final float PITCH_LIMIT = 89.9f;
    private static final float MAX_MAGNITUDE = 2048;
    private static final int FRESH_TICKS = 100;

    private final BooleanWindow window = new BooleanWindow(WINDOW);
    private final StepEstimator yawStep = new StepEstimator();
    private final StepEstimator pitchStep = new StepEstimator();
    private long yawFreshTick = Long.MIN_VALUE / 2;
    private long pitchFreshTick = Long.MIN_VALUE / 2;

    private int minOffGrid;
    private int combatTicks;
    private double minStep;
    private double minDelta;

    public AimGcd(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        minOffGrid = config.getIntElse(getConfigName() + ".min-off-grid", 13);
        combatTicks = config.getIntElse(getConfigName() + ".combat-ticks", 1);
        minStep = config.getDoubleElse(getConfigName() + ".min-step", 0.03);
        minDelta = config.getDoubleElse(getConfigName() + ".min-delta", 0.75);
    }

    @Override
    public void process(final RotationUpdate update) {
        if (!isFeatureEnabled() || !AimExemptions.applies(player)) {
            window.clear();
            return;
        }

        AimProcessor processor = update.getProcessor();
        if (processor == null) return;
        CombatTracker tracker = player.checkManager.getPacketCheck(CombatTracker.class);
        long tick = tracker.currentTick();
        yawStep.observe(update.getDeltaXRot(), processor.modeX);
        pitchStep.observe(update.getDeltaYRot(), processor.modeY);
        if (yawStep.isFresh()) yawFreshTick = tick;
        if (pitchStep.isFresh()) pitchFreshTick = tick;

        if (tracker.ticksSinceAttack() > combatTicks) return;

        Boolean yaw = tick - yawFreshTick > FRESH_TICKS ? null
                : offGrid(update.getDeltaXRot(), yawStep.step(), update.getFrom().yaw(), update.getTo().yaw());
        boolean pitchBounded = Math.abs(update.getFrom().pitch()) > PITCH_LIMIT || Math.abs(update.getTo().pitch()) > PITCH_LIMIT;
        Boolean pitch = pitchBounded || tick - pitchFreshTick > FRESH_TICKS ? null : offGrid(update.getDeltaYRot(), pitchStep.step(), update.getFrom().pitch(), update.getTo().pitch());
        if (yaw == null && pitch == null) {
            if (tick - yawFreshTick > FRESH_TICKS && tick - pitchFreshTick > FRESH_TICKS) window.clear();
            return;
        }

        boolean off = Boolean.TRUE.equals(yaw) || Boolean.TRUE.equals(pitch);
        window.add(off);
        if (!off) reward();

        if (window.isFull() && window.trueCount() >= minOffGrid) {
            flagAndAlert("offGrid=" + window.trueCount() + "/" + WINDOW
                    + ", stepX=" + String.format("%.4f", yawStep.step())
                    + ", stepY=" + String.format("%.4f", pitchStep.step()));
            window.clear();
        }
    }

    private Boolean offGrid(float delta, double step, float from, float to) {
        float magnitude = Math.max(Math.abs(from), Math.abs(to));
        if (step < minStep || magnitude > MAX_MAGNITUDE || Math.abs(delta) < minDelta || Math.abs(delta) > MAX_DELTA) return null;
        return SensitivityGrid.isOffGrid(delta, step, magnitude);
    }
}
