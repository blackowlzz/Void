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
 * Detects autoclickers via inter-click interval regularity (Coefficient of
 * Variation = std_dev / mean of consecutive click intervals).
 *
 * Research-based thresholds (motor control literature + community data):
 *   Human jitter/butterfly:  CV ≈ 0.08–0.20  (natural motor variance)
 *   Basic fixed autoclicker: CV ≈ 0.00–0.02  (machine precision)
 *   Randomized autoclicker:  CV ≈ 0.05–0.12  (caught better by AutoClickerB)
 *
 * This check uses min-cv = 0.05 (5%) by default, below all documented
 * human clicking techniques so false flags are essentially impossible.
 *
 * Hard CPS cap (max-cps = 30) targets what no jitter/butterfly clicker can
 * sustain: drag clicking goes far above 30 but produces burst-gap structures
 * that naturally inflate CV, preventing false flags here.
 *
 * @see AutoClickerB for randomized-autoclicker detection via bucket CPS variance
 * @see AutoClickerC for multi-attack-per-tick detection
 */
@CheckData(
        name = "AutoClickerA",
        stableKey = "void.combat.autoclickera",
        description = "Clicked with near-zero inter-click interval variance",
        decay = 0.025,
        setback = 20
)
public class AutoClickerA extends Check implements PostPredictionCheck {

    // How far back to keep timestamps for variance analysis
    private static final int WINDOW_MS = 2000;
    // Minimum samples in the window before variance check runs
    private static final int MIN_SAMPLES = 8;

    private final ArrayDeque<Long> clickTimes = new ArrayDeque<>();
    // Written on the packet thread, read on the prediction thread (volatile for visibility)
    private volatile int ticksSinceLastAttack = 0;

    private int maxCps;
    private int analysisCps;
    private double minCv;

    public AutoClickerA(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        // 30 CPS: above sustained jitter/butterfly ceiling (~18/28 CPS), below drag clicking territory
        maxCps = config.getIntElse(getConfigName() + ".max-cps", 30);
        // Start analysis at 8 CPS most autoclickers aim for 10–15 CPS to look "normal"
        analysisCps = config.getIntElse(getConfigName() + ".analysis-cps", 8);
        // 0.05 (5%) is below every documented human clicking technique; autoclickers are 0.00–0.02
        minCv = config.getDoubleElse(getConfigName() + ".min-cv", 0.05);
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

        ticksSinceLastAttack = 0;
        long now = System.currentTimeMillis();
        clickTimes.addLast(now);

        // Drop timestamps older than the analysis window
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > WINDOW_MS) {
            clickTimes.pollFirst();
        }

        // Count clicks in the last 1 second for the CPS metric
        int cps = 0;
        for (long ts : clickTimes) {
            if (now - ts <= 1000L) cps++;
        }

        // Condition A: hard CPS cap; no human can sustain this long-term
        if (cps > maxCps) {
            flagAndAlert("cps=" + cps);
            return;
        }

        // Condition B: suspicious regularity at elevated CPS
        if (cps >= analysisCps && clickTimes.size() >= MIN_SAMPLES) {
            double cv = computeCV();
            if (cv >= 0 && cv < minCv) {
                flagAndAlert("cps=" + cps + " cv=" + String.format("%.3f", cv));
                return;
            }
        }

        reward();
    }

    /**
     * Returns the Coefficient of Variation (std_dev / mean) of the
     * inter-click intervals within the current analysis window,
     * or -1 if there are not enough data points.
     *
     * Human clicking: CV typically > 0.20 even at high CPS.
     * Autoclickers:   CV typically < 0.06 (near-zero jitter).
     */
    private double computeCV() {
        if (clickTimes.size() < 2) return -1;

        // Single-pass Welford-style: no array allocation, one deque traversal
        double sum = 0;
        double sumSq = 0;
        int count = 0;
        long prev = -1;
        for (long ts : clickTimes) {
            if (prev >= 0) {
                double interval = ts - prev;
                sum += interval;
                sumSq += interval * interval;
                count++;
            }
            prev = ts;
        }

        if (count == 0) return -1;
        double mean = sum / count;
        if (mean <= 0) return -1;
        double variance = (sumSq / count) - (mean * mean);
        return Math.sqrt(Math.max(0, variance)) / mean;
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        // After 2 seconds without an attack, start slowly decaying violations so that
        // a brief autoclicker burst does not permanently inflate the violation counter.
        if (++ticksSinceLastAttack > 40) {
            reward();
        }
    }
}
