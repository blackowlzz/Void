package ac.voidac.manager;

import ac.voidac.checks.Check;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adaptive threshold tuner for Void.
 *
 * While running, every flag that passes through {@code PunishmentManager} is recorded.
 * Flags from players holding {@code void.optimizer.legit} are treated as false-positives;
 * flags from everyone else are treated as untrusted samples.
 *
 * On {@link #save(File)} the manager writes a human-readable report to
 * {@code threshold-optimizer-report.yml}. The report lists per-check recommendations
 * that you can apply to {@code punishments.yml} manually.
 *
 * Designed to run for hours/days; the hot path ({@link #recordFlag}) is a no-op when
 * the optimizer is not running.
 */
public class ThresholdOptimizerManager {

    /** Multiplier applied to the worst legit VL to derive the recommended threshold. */
    private static final double SAFETY_MULTIPLIER = 1.5;

    /** Minimum sample size of legit flags before we trust a recommendation. */
    private static final int MIN_LEGIT_SAMPLES = 5;

    private static final String LEGIT_PERMISSION = "void.optimizer.legit";

    private static final DateTimeFormatter REPORT_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);


    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, CheckStats> checkStats = new ConcurrentHashMap<>();
    private volatile long startedAt = 0L;
    private volatile long stoppedAt = 0L;

    // ── Lifecycle ────────────────────────────────────────────────────────

    public boolean isRunning() { return running.get(); }

    public boolean start() {
        if (!running.compareAndSet(false, true)) return false;
        startedAt = System.currentTimeMillis();
        stoppedAt = 0L;
        LogUtil.info("[Optimizer] Started — observing flags. Per-flag analysis is active, expect a small CPU overhead until stopped.");
        return true;
    }

    public boolean stop() {
        if (!running.compareAndSet(true, false)) return false;
        stoppedAt = System.currentTimeMillis();
        LogUtil.info("[Optimizer] Stopped — " + checkStats.size() + " checks observed. Run /void optimizer save to persist recommendations.");
        return true;
    }

    public void discard() {
        checkStats.clear();
        startedAt = 0L;
        stoppedAt = 0L;
    }

    // ── Recording ────────────────────────────────────────────────────────

    /**
     * Hot path: called from {@code PunishmentManager.handleAlert} for every flag.
     * Returns immediately when the optimizer is not running.
     */
    public void recordFlag(VoidPlayer player, Check check, int vl) {
        if (!running.get()) return;
        if (check == null || check.getCheckName() == null) return;

        boolean legit = player.platformPlayer != null && player.platformPlayer.hasPermission(LEGIT_PERMISSION);
        CheckStats stats = checkStats.computeIfAbsent(check.getCheckName(), k -> new CheckStats());
        stats.record(player.uuid, vl, legit);
    }

    // ── Reporting ────────────────────────────────────────────────────────

    public List<String> getStatusLines() {
        List<String> lines = new ArrayList<>();
        lines.add(running.get() ? "&aRunning" : "&cStopped");
        if (startedAt > 0) {
            lines.add("Started: &f" + REPORT_DATE.format(Instant.ofEpochMilli(startedAt)) + " UTC");
        }
        if (stoppedAt > 0) {
            lines.add("Stopped: &f" + REPORT_DATE.format(Instant.ofEpochMilli(stoppedAt)) + " UTC");
        }
        lines.add("Checks observed: &f" + checkStats.size());

        long totalLegit = 0, totalUntrusted = 0;
        Set<UUID> legitPlayers = new HashSet<>(), untrustedPlayers = new HashSet<>();
        for (CheckStats s : checkStats.values()) {
            totalLegit     += s.legitFlagCount;
            totalUntrusted += s.untrustedFlagCount;
            legitPlayers.addAll(s.legitPlayers);
            untrustedPlayers.addAll(s.untrustedPlayers);
        }
        lines.add("Legit flags: &f" + totalLegit + " &7from &f" + legitPlayers.size() + " &7players");
        lines.add("Untrusted flags: &f" + totalUntrusted + " &7from &f" + untrustedPlayers.size() + " &7players");

        if (checkStats.isEmpty()) return lines;

        List<Map.Entry<String, CheckStats>> sorted = new ArrayList<>(checkStats.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue().legitMaxVl, a.getValue().legitMaxVl));
        int shown = 0;
        for (Map.Entry<String, CheckStats> e : sorted) {
            CheckStats s = e.getValue();
            if (s.legitFlagCount == 0) continue;
            lines.add("  &8▸ &d" + e.getKey()
                    + " &8│ &7legit max VL &f" + (int) s.legitMaxVl
                    + " &8│ &7samples &f" + s.legitFlagCount
                    + (s.legitFlagCount < MIN_LEGIT_SAMPLES ? " &c(low)" : ""));
            if (++shown >= 8) {
                lines.add("  &8... &7(" + (sorted.size() - shown) + " more)");
                break;
            }
        }
        return lines;
    }

    // ── Save ─────────────────────────────────────────────────────────────

    public SaveResult save(File dataFolder) {
        if (checkStats.isEmpty()) {
            return new SaveResult(false, 0, 0, "no data to save");
        }

        Map<String, Integer> recommendations = buildRecommendations();
        int reportWritten = writeReport(dataFolder, recommendations);

        return new SaveResult(true, reportWritten, 0, "report=" + reportWritten + " entries written");
    }

    private Map<String, Integer> buildRecommendations() {
        Map<String, Integer> recs = new HashMap<>();
        for (Map.Entry<String, CheckStats> e : checkStats.entrySet()) {
            CheckStats s = e.getValue();
            if (s.legitFlagCount < MIN_LEGIT_SAMPLES) continue;
            int recommended = (int) Math.ceil(s.legitMaxVl * SAFETY_MULTIPLIER);
            if (recommended <= 0) continue;
            recs.put(e.getKey().toLowerCase(Locale.ROOT), recommended);
        }
        return recs;
    }

    private int writeReport(File dataFolder, Map<String, Integer> recommendations) {
        dataFolder.mkdirs();
        File reportFile = new File(dataFolder, "threshold-optimizer-report.yml");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(reportFile))) {
            w.write("# Void Threshold Optimizer — recommendations\n");
            w.write("# Generated: " + REPORT_DATE.format(Instant.now()) + " UTC\n");
            w.write("# Started:   " + (startedAt > 0 ? REPORT_DATE.format(Instant.ofEpochMilli(startedAt)) + " UTC" : "n/a") + "\n");
            w.write("# Stopped:   " + (stoppedAt > 0 ? REPORT_DATE.format(Instant.ofEpochMilli(stoppedAt)) + " UTC" : "n/a") + "\n");
            w.write("#\n");
            w.write("# Format: <check>: { legit_max_vl, legit_samples, untrusted_samples, recommended_threshold }\n");
            w.write("# Recommended = legit_max_vl * " + SAFETY_MULTIPLIER + " (rounded up)\n");
            w.write("# Entries with fewer than " + MIN_LEGIT_SAMPLES + " legit samples are marked 'insufficient-data'.\n");
            w.write("# Apply these thresholds to punishments.yml manually.\n\n");
            w.write("checks:\n");

            List<Map.Entry<String, CheckStats>> sorted = new ArrayList<>(checkStats.entrySet());
            sorted.sort(Map.Entry.comparingByKey());

            int count = 0;
            for (Map.Entry<String, CheckStats> e : sorted) {
                CheckStats s = e.getValue();
                int rec = recommendations.getOrDefault(e.getKey().toLowerCase(Locale.ROOT), -1);
                w.write("  " + e.getKey() + ":\n");
                w.write("    legit_max_vl: " + (int) s.legitMaxVl + "\n");
                w.write("    legit_samples: " + s.legitFlagCount + "\n");
                w.write("    legit_unique_players: " + s.legitPlayers.size() + "\n");
                w.write("    untrusted_samples: " + s.untrustedFlagCount + "\n");
                w.write("    untrusted_unique_players: " + s.untrustedPlayers.size() + "\n");
                w.write("    recommended_threshold: " + (rec > 0 ? rec : "insufficient-data") + "\n");
                if (rec > 0) count++;
            }
            return count;
        } catch (IOException e) {
            LogUtil.error("[Optimizer] Failed to write report", e);
            return 0;
        }
    }


    // ── Inner types ──────────────────────────────────────────────────────

    private static class CheckStats {
        volatile double legitMaxVl = 0;
        volatile long   legitFlagCount = 0;
        volatile long   untrustedFlagCount = 0;
        final Set<UUID> legitPlayers     = ConcurrentHashMap.newKeySet();
        final Set<UUID> untrustedPlayers = ConcurrentHashMap.newKeySet();

        synchronized void record(UUID uuid, int vl, boolean legit) {
            if (legit) {
                legitFlagCount++;
                legitPlayers.add(uuid);
                if (vl > legitMaxVl) legitMaxVl = vl;
            } else {
                untrustedFlagCount++;
                untrustedPlayers.add(uuid);
            }
        }
    }

    public record SaveResult(boolean ok, int recommendations, int linesPatched, String message) {}
}
