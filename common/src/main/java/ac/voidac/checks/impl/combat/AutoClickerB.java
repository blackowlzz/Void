package ac.voidac.checks.impl.combat;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PostPredictionCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.ArrayDeque;

/**
 * Detects autoclickers by measuring the consistency of per-second CPS averages
 * over a rolling window.
 *
 * AutoClickerA catches fixed-interval autoclickers via per-click CV.
 * This check catches *randomized* autoclickers: tools that add per-click jitter
 * to evade simple CV checks but still produce a remarkably flat CPS average
 * across multiple seconds (they cannot fake the human tendency to vary rate
 * across different combat moments).
 *
 * Metric: Coefficient of Variation of per-second click buckets.
 *   Human (any technique): bucket CV typically 0.20–0.60 (rate varies second-to-second)
 *   Randomized autoclicker: bucket CV typically 0.04–0.12 (flat average rate)
 *
 * Research reference: CPS-count studies + motor control literature (PMC5992087).
 *
 * @see AutoClickerA for per-click interval regularity detection
 * @see AutoClickerC for multi-attack-per-tick detection
 */
@CheckData(
        name = "AutoClickerB",
        stableKey = "void.combat.autoclickerb",
        description = "Maintained a suspiciously consistent click rate over time",
        decay = 0.02,
        setback = 20
)
public class AutoClickerB extends Check implements PostPredictionCheck {

    private static final int BUCKET_MS = 1000;  // 1-second buckets
    private static final int MIN_BUCKETS = 10;  // 10 seconds of data before analysis

    // Completed full 1-second buckets (click counts)
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
        // Mean CPS across buckets must be at least this to trigger analysis
        analysisCps = config.getIntElse(getConfigName() + ".analysis-cps", 8);
        // Bucket CV below this = suspiciously flat rate. Autoclickers: 0.04-0.08. Humans: 0.15+.
        // 0.10 is safe: catches bots, cannot reach legitimate butterfly/jitter clickers.
        minBucketCv = config.getDoubleElse(getConfigName() + ".min-bucket-cv", 0.10);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.disableVoid) return;
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) return;

        boolean isAttack = event.getPacketType() == PacketType.Play.Client.ATTACK;
        if (!isAttack && event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            isAttack = packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        }
        if (!isAttack) return;

        // If the player was idle for 2+ seconds, reset bucket state here (on the packet thread)
        // so all ArrayDeque operations stay on a single thread.
        if (ticksSinceLastAttack > 40) {
            completedBuckets.clear();
            currentBucketClicks = 0;
            currentBucketStart = -1;
        }
        ticksSinceLastAttack = 0;
        long now = System.currentTimeMillis();

        // Initialize or advance bucket
        if (currentBucketStart == -1) {
            currentBucketStart = now;
        } else if (now - currentBucketStart >= BUCKET_MS) {
            // Finalize the completed bucket
            completedBuckets.addLast(currentBucketClicks);
            if (completedBuckets.size() > MIN_BUCKETS + 2) {
                completedBuckets.pollFirst();
            }
            currentBucketClicks = 0;
            currentBucketStart = now;
        }
        currentBucketClicks++;

        // Wait for enough complete buckets before analyzing
        if (completedBuckets.size() < MIN_BUCKETS) return;

        // Mean clicks per completed bucket (= mean CPS per second)
        double sum = 0;
        for (int c : completedBuckets) sum += c;
        double mean = sum / completedBuckets.size();

        if (mean < analysisCps) {
            reward();
            return;
        }

        // Coefficient of Variation of bucket click counts
        double variance = 0;
        for (int c : completedBuckets) {
            double d = c - mean;
            variance += d * d;
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
        if (++ticksSinceLastAttack > 40) {
            reward();
        }
    }
}
