package ac.voidac.manager;

import ac.voidac.checks.Check;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThresholdOptimizerManager {
    private static final double SAFETY_MULTIPLIER = 1.5;
    private static final int MIN_LEGIT_SAMPLES = 5;
    private static final int STATUS_CHECK_LIMIT = 8;
    private static final String LEGIT_PERMISSION = "void.optimizer.legit";
    private static final DateTimeFormatter REPORT_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, CheckStats> checkStats = new ConcurrentHashMap<>();
    private volatile long startedAt;
    private volatile long stoppedAt;

    public boolean isRunning() {
        return running.get();
    }

    public boolean start() {
        if (!running.compareAndSet(false, true)) return false;
        startedAt = System.currentTimeMillis();
        stoppedAt = 0L;
        LogUtil.info("[Optimizer] Started, observing flags. Per-flag analysis is active, expect a small CPU overhead until stopped.");
        return true;
    }

    public boolean stop() {
        if (!running.compareAndSet(true, false)) return false;
        stoppedAt = System.currentTimeMillis();
        LogUtil.info("[Optimizer] Stopped, " + checkStats.size() + " checks observed. Run /void optimizer save to persist recommendations.");
        return true;
    }

    public void discard() {
        checkStats.clear();
        startedAt = 0L;
        stoppedAt = 0L;
    }

    public void recordFlag(VoidPlayer player, Check check, int vl) {
        if (!running.get() || check == null || check.getCheckName() == null) return;

        boolean legit = player.platformPlayer != null && player.platformPlayer.hasPermission(LEGIT_PERMISSION);
        checkStats.computeIfAbsent(check.getCheckName(), k -> new CheckStats()).record(player.uuid, vl, legit);
    }

    public List<String> getStatusLines() {
        List<String> lines = new ArrayList<>();
        lines.add(running.get() ? "&a<sc>Running</sc>" : "&c<sc>Stopped</sc>");
        if (startedAt > 0) {
            lines.add("&7<sc>Started</sc>&8: &f" + formatTime(startedAt) + " UTC");
        }
        if (stoppedAt > 0) {
            lines.add("&7<sc>Stopped</sc>&8: &f" + formatTime(stoppedAt) + " UTC");
        }
        lines.add("&7<sc>Checks observed</sc>&8: &f" + checkStats.size());

        long totalLegit = 0;
        long totalUntrusted = 0;
        Set<UUID> legitPlayers = new HashSet<>();
        Set<UUID> untrustedPlayers = new HashSet<>();
        for (CheckStats stats : checkStats.values()) {
            synchronized (stats) {
                totalLegit += stats.legitFlagCount;
                totalUntrusted += stats.untrustedFlagCount;
                legitPlayers.addAll(stats.legitPlayers);
                untrustedPlayers.addAll(stats.untrustedPlayers);
            }
        }
        lines.add("&7<sc>Legit flags</sc>&8: &f" + totalLegit + " &7<sc>from</sc> &f" + legitPlayers.size() + " &7<sc>players</sc>");
        lines.add("&7<sc>Untrusted flags</sc>&8: &f" + totalUntrusted + " &7<sc>from</sc> &f" + untrustedPlayers.size() + " &7<sc>players</sc>");

        List<LegitSummary> withLegitFlags = new ArrayList<>();
        for (Map.Entry<String, CheckStats> entry : checkStats.entrySet()) {
            CheckStats stats = entry.getValue();
            synchronized (stats) {
                if (stats.legitFlagCount > 0) {
                    withLegitFlags.add(new LegitSummary(entry.getKey(), stats.legitMaxVl, stats.legitFlagCount));
                }
            }
        }
        withLegitFlags.sort(Comparator.comparingInt(LegitSummary::maxVl).reversed());

        int shown = Math.min(STATUS_CHECK_LIMIT, withLegitFlags.size());
        for (LegitSummary summary : withLegitFlags.subList(0, shown)) {
            lines.add("  &8▸ &d" + summary.checkName()
                    + " &8│ &7<sc>legit max vl</sc> &f" + summary.maxVl()
                    + " &8│ &7<sc>samples</sc> &f" + summary.samples()
                    + (summary.samples() < MIN_LEGIT_SAMPLES ? " &c(<sc>low</sc>)" : ""));
        }
        if (withLegitFlags.size() > shown) {
            lines.add("  &8... &7(" + (withLegitFlags.size() - shown) + " <sc>more</sc>)");
        }
        return lines;
    }

    public SaveResult save(File dataFolder) {
        if (checkStats.isEmpty()) {
            return new SaveResult(false, 0, "no data to save");
        }
        int recommendations = writeReport(dataFolder);
        return new SaveResult(true, recommendations, "report=" + recommendations + " entries written");
    }

    private int writeReport(File dataFolder) {
        dataFolder.mkdirs();
        File reportFile = new File(dataFolder, "threshold-optimizer-report.yml");
        try (BufferedWriter w = Files.newBufferedWriter(reportFile.toPath(), StandardCharsets.UTF_8)) {
            w.write("# Void Threshold Optimizer: recommendations\n");
            w.write("# Generated: " + REPORT_DATE.format(Instant.now()) + " UTC\n");
            w.write("# Started:   " + (startedAt > 0 ? formatTime(startedAt) + " UTC" : "n/a") + "\n");
            w.write("# Stopped:   " + (stoppedAt > 0 ? formatTime(stoppedAt) + " UTC" : "n/a") + "\n");
            w.write("#\n");
            w.write("# Format: <check>: { legit_max_vl, legit_samples, untrusted_samples, recommended_threshold }\n");
            w.write("# Recommended = legit_max_vl * " + SAFETY_MULTIPLIER + " (rounded up)\n");
            w.write("# Entries with fewer than " + MIN_LEGIT_SAMPLES + " legit samples are marked 'insufficient-data'.\n");
            w.write("# Apply these thresholds to punishments.yml manually.\n\n");
            w.write("checks:\n");

            List<Map.Entry<String, CheckStats>> sorted = new ArrayList<>(checkStats.entrySet());
            sorted.sort(Map.Entry.comparingByKey());

            int count = 0;
            for (Map.Entry<String, CheckStats> entry : sorted) {
                CheckStats stats = entry.getValue();
                int recommended = stats.recommendedThreshold();
                w.write("  " + entry.getKey() + ":\n");
                w.write("    legit_max_vl: " + stats.legitMaxVl + "\n");
                w.write("    legit_samples: " + stats.legitFlagCount + "\n");
                w.write("    legit_unique_players: " + stats.legitPlayers.size() + "\n");
                w.write("    untrusted_samples: " + stats.untrustedFlagCount + "\n");
                w.write("    untrusted_unique_players: " + stats.untrustedPlayers.size() + "\n");
                w.write("    recommended_threshold: " + (recommended > 0 ? recommended : "insufficient-data") + "\n");
                if (recommended > 0) count++;
            }
            return count;
        } catch (IOException e) {
            LogUtil.error("[Optimizer] Failed to write report", e);
            return 0;
        }
    }

    private static String formatTime(long epochMillis) {
        return REPORT_DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    private static final class CheckStats {
        private volatile int legitMaxVl;
        private volatile long legitFlagCount;
        private volatile long untrustedFlagCount;
        private final Set<UUID> legitPlayers = ConcurrentHashMap.newKeySet();
        private final Set<UUID> untrustedPlayers = ConcurrentHashMap.newKeySet();

        synchronized void record(UUID uuid, int vl, boolean legit) {
            if (legit) {
                legitFlagCount++;
                legitPlayers.add(uuid);
                legitMaxVl = Math.max(legitMaxVl, vl);
            } else {
                untrustedFlagCount++;
                untrustedPlayers.add(uuid);
            }
        }

        int recommendedThreshold() {
            if (legitFlagCount < MIN_LEGIT_SAMPLES) return -1;
            int recommended = (int) Math.ceil(legitMaxVl * SAFETY_MULTIPLIER);
            return recommended > 0 ? recommended : -1;
        }
    }

    private record LegitSummary(String checkName, int maxVl, long samples) {
    }

    public record SaveResult(boolean ok, int recommendations, String message) {}
}
