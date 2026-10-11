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
        name = "AutoClickerB",
        stableKey = "void.combat.autoclickerb",
        description = "Maintained a suspiciously consistent click rate over time",
        decay = 0.02,
        setback = 20
)
public class AutoClickerB extends Check implements PostPredictionCheck {
    private static final int BUCKET_MS = 1000;
    private static final int MIN_BUCKETS = 10;

    private final ArrayDeque<Integer> completedBuckets = new ArrayDeque<>();
    private int currentBucketClicks = 0;
    private long currentBucketStart = -1;

    private volatile int ticksSinceLastAttack = 0;

    private int analysisCps;
    private double minBucketCv;

    public AutoClickerB(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        analysisCps = config.getIntElse(getConfigName() + ".analysis-cps", 8);
        minBucketCv = config.getDoubleElse(getConfigName() + ".min-bucket-cv", 0.10);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (AttackPackets.isExempt(player) || !AttackPackets.isAttack(event)) return;

        if (ticksSinceLastAttack > AttackPackets.IDLE_TICKS) {
            completedBuckets.clear();
            currentBucketClicks = 0;
            currentBucketStart = -1;
        }
        ticksSinceLastAttack = 0;
        long now = System.currentTimeMillis();

        if (currentBucketStart == -1) {
            currentBucketStart = now;
        } else if (now - currentBucketStart >= BUCKET_MS) {
            completedBuckets.addLast(currentBucketClicks);
            if (completedBuckets.size() > MIN_BUCKETS + 2) {
                completedBuckets.pollFirst();
            }
            currentBucketClicks = 0;
            currentBucketStart = now;
        }
        currentBucketClicks++;

        if (completedBuckets.size() < MIN_BUCKETS) return;

        double sum = 0;
        for (int clicks : completedBuckets) sum += clicks;
        double mean = sum / completedBuckets.size();

        if (mean < analysisCps) {
            reward();
            return;
        }

        double variance = 0;
        for (int clicks : completedBuckets) {
            double deviation = clicks - mean;
            variance += deviation * deviation;
        }
        variance /= completedBuckets.size();
        double cv = Math.sqrt(variance) / mean;

        if (cv < minBucketCv) {
            flagAndAlert("mean_cps=" + String.format("%.1f", mean)
                    + " bucket_cv=" + String.format("%.3f", cv));
        } else {
            reward();
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (++ticksSinceLastAttack > AttackPackets.IDLE_TICKS) {
            reward();
        }
    }
}
