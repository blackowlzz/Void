package ac.voidac.manager;

import ac.voidac.VoidAPI;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.manager.punishment.PunishmentDatabase;
import ac.voidac.manager.punishment.PunishmentDuration;
import ac.voidac.manager.punishment.PunishmentReason;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.platform.api.scheduler.TaskHandle;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BanWaveManager {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final Map<UUID, BanEntry> queue = new LinkedHashMap<>();
    private int  waveNumber         = 0;
    private long lastAutoExecuteTime = 0;
    private File dataFile;
    private volatile TaskHandle autoWaveTask;

    private volatile boolean enabled = false;
    private volatile boolean queueOnAutoPunish = false;
    private volatile int autoExecuteDays = 7;
    private String  duration          = "permanent";
    private String  defaultReason     = "Cheating";
    private String  reasonTemplate    =
            "&4&lVoid AntiCheat\n&c&lBan Wave &4#{wave}\n\n&7You were detected cheating on this server.";
    private String  broadcastTemplate =
            "&8[&5Void&8] &c&lBan Wave &d#{wave}&7: &f{count} &7cheaters have been removed.";
    private boolean preferCustomBan  = false;
    private String  customBanCommand = "litebans:tempban {player} {duration} {reason}";

    public void init(File dataFolder) {
        this.dataFile = new File(dataFolder, "banwave.dat");
        load();
        scheduleAutoExecution();
    }

    public void reload(ConfigManager config) {
        enabled           = config.getBooleanElse("ban-wave.enabled", false);
        queueOnAutoPunish = config.getBooleanElse("ban-wave.queue-on-auto-punish", false);
        autoExecuteDays   = config.getIntElse("ban-wave.auto-execute-days", 7);
        duration          = normalizeDuration(config.getStringElse("ban-wave.duration", "7d"), "7d", "ban-wave.duration");
        defaultReason     = config.getStringElse("ban-wave.default-reason", "Cheating");
        reasonTemplate    = config.getStringElse("ban-wave.reason", reasonTemplate);
        broadcastTemplate = config.getStringElse("ban-wave.broadcast", broadcastTemplate);
        preferCustomBan   = config.getBooleanElse("ban-wave.prefer-custom-ban", false);
        customBanCommand  = config.getStringElse("ban-wave.custom-ban-command", "litebans:tempban {player} {duration} {reason}");

        if (dataFile != null) {
            scheduleAutoExecution();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isQueueOnAutoPunish() {
        return queueOnAutoPunish;
    }

    public synchronized int getWaveNumber() {
        return waveNumber;
    }

    public synchronized int getQueueSize() {
        return queue.size();
    }

    public synchronized Map<UUID, BanEntry> getQueue() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(queue));
    }

    public synchronized boolean addToQueue(UUID uuid, String name, String check, String addedBy) {
        return addToQueue(uuid, name, check, addedBy, null, null);
    }

    public synchronized boolean addToQueue(UUID uuid, String name, String check, String addedBy,
                                           @Nullable String duration, @Nullable String reason) {
        if (queue.containsKey(uuid)) return false;
        queue.put(uuid, new BanEntry(uuid, name, check, addedBy, System.currentTimeMillis(), duration, reason));
        save();
        return true;
    }

    public synchronized UUID removeFromQueueByName(String name) {
        for (Iterator<Map.Entry<UUID, BanEntry>> it = queue.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, BanEntry> e = it.next();
            if (e.getValue().name().equalsIgnoreCase(name)) {
                UUID uuid = e.getKey();
                it.remove();
                save();
                return uuid;
            }
        }
        return null;
    }

    public synchronized void clearQueue() {
        queue.clear();
        save();
    }

    public synchronized int executeWave() {
        if (queue.isEmpty()) return 0;

        waveNumber++;
        lastAutoExecuteTime = System.currentTimeMillis();
        String waveStr = String.valueOf(waveNumber);

        List<BanEntry> toBan = new ArrayList<>(queue.values());
        queue.clear();
        save();

        for (BanEntry entry : toBan) {
            executeBan(entry, waveStr);
        }

        String rawBroadcast = broadcastTemplate
                .replace("{wave}", waveStr)
                .replace("{count}", String.valueOf(toBan.size()));
        Component broadcast = MessageUtil.miniMessage(rawBroadcast);
        for (PlatformPlayer player : VoidAPI.INSTANCE.getPlatformPlayerFactory().getOnlinePlayers()) {
            player.sendMessage(broadcast);
        }

        LogUtil.info("[BanWave] Wave #" + waveStr + " executed: " + toBan.size() + " players banned.");
        return toBan.size();
    }

    public String formatKickReason(BanEntry entry, String waveStr) {
        String raw = reasonTemplate
                .replace("{wave}", waveStr)
                .replace("{player}", entry.name())
                .replace("{check}", entry.check());
        raw = PunishmentReason.applyToTemplate(raw, resolveReason(entry.reason()));
        return MessageUtil.translateAlternateColorCodes('&', raw);
    }

    public String resolveReason(@Nullable String reason) {
        return PunishmentReason.resolve(reason, defaultReason);
    }

    public String formatEntry(int index, BanEntry entry) {
        String time = DATE_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()));
        String dur  = entry.duration() != null ? entry.duration() : "config default";
        String reason = resolveReason(entry.reason());
        return "  &8" + index + ". &f" + entry.name()
                + " &8│ &7<sc>check</sc>&8: &d" + entry.check()
                + " &8│ &7<sc>by</sc>&8: &f" + entry.addedBy()
                + " &8│ &7<sc>duration</sc>&8: &f" + dur
                + " &8│ &7<sc>reason</sc>&8: &f" + reason
                + " &8│ &7<sc>added</sc>&8: &7" + time + " UTC";
    }

    private void scheduleAutoExecution() {
        if (autoWaveTask != null) {
            autoWaveTask.cancel();
            autoWaveTask = null;
        }
        if (!enabled || autoExecuteDays <= 0) return;

        autoWaveTask = VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(
                VoidAPI.INSTANCE.getVoidPlugin(),
                this::checkAndAutoExecute,
                1, 1, TimeUnit.HOURS
        );
    }

    private void checkAndAutoExecute() {
        if (!enabled || autoExecuteDays <= 0) return;
        synchronized (this) {
            if (queue.isEmpty()) return;
            long oldestQueuedAt = queue.values().stream().mapToLong(BanEntry::timestamp).min().orElse(0L);
            if (!isAutoExecutionDue(System.currentTimeMillis(), lastAutoExecuteTime, oldestQueuedAt, autoExecuteDays)) return;
        }

        LogUtil.info("[BanWave] Auto-executing scheduled wave (interval: " + autoExecuteDays + "d)...");
        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                VoidAPI.INSTANCE.getVoidPlugin(),
                this::executeWave
        );
    }

    static boolean isAutoExecutionDue(long now, long lastExecutionAt, long oldestQueuedAt, int intervalDays) {
        if (intervalDays <= 0 || oldestQueuedAt <= 0) {
            return false;
        }
        long reference = lastExecutionAt > 0 ? lastExecutionAt : oldestQueuedAt;
        return now >= reference && now - reference >= TimeUnit.DAYS.toMillis(intervalDays);
    }

    private void executeBan(BanEntry entry, String waveStr) {
        String effectiveDuration = normalizeDuration(entry.duration() != null ? entry.duration() : duration,
                duration, "ban-wave queue entry for " + entry.name());
        String banId   = VoidAPI.INSTANCE.getPunishmentDatabase().reserveBanId();
        String dateStr = PunishmentDatabase.DATE_FORMAT_DISPLAY.format(Instant.now());

        String kickReason = formatKickReason(entry, waveStr)
                .replace("{ban_id}",  banId)
                .replace("{date}",    dateStr)
                .replace("{duration}", effectiveDuration);
        String banReason = toBanCommandLine(kickReason);

        VoidPlayer voidPlayer = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(entry.uuid());
        boolean isOffline = voidPlayer == null || voidPlayer.platformPlayer == null;
        if (!isOffline) {
            final PlatformPlayer pp = voidPlayer.platformPlayer;
            VoidAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                    pp, VoidAPI.INSTANCE.getVoidPlugin(),
                    () -> pp.kickPlayer(kickReason), null, 0L
            );
        }

        if (preferCustomBan) {
            dispatchCommand(customBanCommand
                    .replace("{player}", entry.name())
                    .replace("{duration}", effectiveDuration)
                    .replace("{reason}", banReason));
        }
        if (!preferCustomBan || isOffline) {
            VoidAPI.INSTANCE.getVoidBanManager().ban(banId, entry.uuid(), entry.name(), kickReason, effectiveDuration, System.currentTimeMillis());
        }

        VoidAPI.INSTANCE.getPunishmentDatabase().insertWithId(
                banId,
                entry.uuid(),
                entry.name(),
                "banwave",
                entry.addedBy(),
                "auto-punish".equals(entry.addedBy()) ? entry.check() : null,
                banReason,
                effectiveDuration,
                waveNumber,
                null,
                entry.timestamp()
        );
    }

    private static String toBanCommandLine(String richReason) {
        return PunishmentReason.toSingleLine(richReason, " §8| §r");
    }

    private void dispatchCommand(String command) {
        VoidAPI.INSTANCE.getPlatformServer().dispatchCommand(
                VoidAPI.INSTANCE.getPlatformServer().getConsoleSender(), command
        );
    }

    private String normalizeDuration(String value, String fallback, String source) {
        try {
            return PunishmentDuration.normalize(value);
        } catch (IllegalArgumentException exception) {
            LogUtil.warn("Invalid punishment duration in " + source + ": '" + value + "'. Using '" + fallback + "'.");
            return PunishmentDuration.normalize(fallback);
        }
    }

    private synchronized void load() {
        if (dataFile == null || !dataFile.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(dataFile.toPath()), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            if (line != null) waveNumber = Integer.parseInt(line.trim());

            line = r.readLine();
            if (line != null) lastAutoExecuteTime = Long.parseLong(line.trim());

            queue.clear();
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    BanEntry entry = parseEntry(line);
                    queue.put(entry.uuid(), entry);
                } catch (IllegalArgumentException e) {
                    LogUtil.warn("[BanWave] Skipping invalid banwave.dat entry: " + line);
                }
            }
            LogUtil.info("[BanWave] Loaded: " + queue.size() + " queued players (wave #" + waveNumber + ").");
        } catch (IOException | NumberFormatException e) {
            LogUtil.error("[BanWave] Failed to load banwave.dat", e);
        }
    }

    private synchronized void save() {
        if (dataFile == null) return;
        try {
            dataFile.getParentFile().mkdirs();
            Path target = dataFile.toPath();
            Path temp = target.resolveSibling(dataFile.getName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                w.write(String.valueOf(waveNumber));
                w.newLine();
                w.write(String.valueOf(lastAutoExecuteTime));
                w.newLine();
                for (BanEntry e : queue.values()) {
                    w.write(serializeEntry(e));
                    w.newLine();
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LogUtil.error("[BanWave] Failed to save banwave.dat", e);
        }
    }

    static String serializeEntry(BanEntry entry) {
        String encodedReason = entry.reason() == null ? "" : Base64.getUrlEncoder().withoutPadding()
                .encodeToString(entry.reason().getBytes(StandardCharsets.UTF_8));
        return entry.uuid() + "|" + entry.name() + "|" + entry.check()
                + "|" + entry.addedBy() + "|" + entry.timestamp()
                + "|" + (entry.duration() != null ? entry.duration() : "")
                + "|" + encodedReason;
    }

    static BanEntry parseEntry(String line) {
        String[] parts = line.split("\\|", 7);
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid ban wave entry");
        }
        long timestamp = parts.length >= 5 ? Long.parseLong(parts[4]) : System.currentTimeMillis();
        String entryDuration = parts.length >= 6 && !parts[5].isBlank() ? parts[5] : null;
        String entryReason = parts.length >= 7 && !parts[6].isBlank()
                ? new String(Base64.getUrlDecoder().decode(parts[6]), StandardCharsets.UTF_8)
                : null;
        return new BanEntry(UUID.fromString(parts[0]), parts[1], parts[2], parts[3], timestamp, entryDuration, entryReason);
    }

    public record BanEntry(UUID uuid, String name, String check, String addedBy, long timestamp,
                           @Nullable String duration, @Nullable String reason) {
    }
}
