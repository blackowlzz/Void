package ac.voidac.checks.impl.combat;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PostPredictionCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;

import java.util.ArrayDeque;

@CheckData(
        name = "AutoClickerA",
        stableKey = "void.combat.autoclickera",
        description = "Clicked with near-zero inter-click interval variance",
        decay = 0.025,
        setback = 20
)
public class AutoClickerA extends Check implements PostPredictionCheck {

    private static final int WINDOW_MS = 2000;
    private static final long CPS_WINDOW_MS = 1000L;
    private static final int MIN_SAMPLES = 8;

    private final ArrayDeque<Long> clickTimes = new ArrayDeque<>();
    private volatile int ticksSinceLastAttack = 0;

    private int maxCps;
    private int analysisCps;
    private double minCv;

    public AutoClickerA(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxCps = config.getIntElse(getConfigName() + ".max-cps", 30);
        analysisCps = config.getIntElse(getConfigName() + ".analysis-cps", 8);
        minCv = config.getDoubleElse(getConfigName() + ".min-cv", 0.05);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (AttackPackets.isExempt(player) || !AttackPackets.isAttack(event)) return;

        ticksSinceLastAttack = 0;
        long now = System.currentTimeMillis();
        clickTimes.addLast(now);

        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > WINDOW_MS) {
            clickTimes.pollFirst();
        }

        int cps = 0;
        for (long timestamp : clickTimes) {
            if (now - timestamp <= CPS_WINDOW_MS) cps++;
        }

        if (cps > maxCps) {
            flagAndAlert("cps=" + cps);
            return;
        }

        if (cps >= analysisCps && clickTimes.size() >= MIN_SAMPLES) {
            double cv = intervalVariationCoefficient();
            if (cv >= 0 && cv < minCv) {
                flagAndAlert("cps=" + cps + " cv=" + String.format("%.3f", cv));
                return;
            }
        }

        reward();
    }

    private double intervalVariationCoefficient() {
        if (clickTimes.size() < 2) return -1;

        double sum = 0;
        double sumSquares = 0;
        int count = 0;
        long previous = -1;
        for (long timestamp : clickTimes) {
            if (previous >= 0) {
                double interval = timestamp - previous;
                sum += interval;
                sumSquares += interval * interval;
                count++;
            }
            previous = timestamp;
        }

        if (count == 0) return -1;
        double mean = sum / count;
        if (mean <= 0) return -1;
        double variance = (sumSquares / count) - (mean * mean);
        return Math.sqrt(Math.max(0, variance)) / mean;
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (++ticksSinceLastAttack > AttackPackets.IDLE_TICKS) {
            reward();
        }
    }
}
