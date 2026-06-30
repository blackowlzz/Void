package ac.voidac.manager;

import ac.voidac.VoidAPI;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.manager.punishment.PunishmentDatabase;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.platform.api.scheduler.TaskHandle;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BanWaveManager {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final Map<UUID, BanEntry> queue = new LinkedHashMap<>();
    private int  waveNumber         = 0;
    private long lastAutoExecuteTime = 0;
    private File dataFile;
    private volatile TaskHandle autoWaveTask;

    private boolean enabled           = false;
    private boolean queueOnAutoPunish = false;
    private int     autoExecuteDays   = 7;
    private String  duration          = "permanent";
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
        duration          = config.getStringElse("ban-wave.duration", "permanent");
        reasonTemplate    = config.getStringElse("ban-wave.reason", reasonTemplate);
        broadcastTemplate = config.getStringElse("ban-wave.broadcast", broadcastTemplate);
        preferCustomBan   = config.getBooleanElse("ban-wave.prefer-custom-ban", false);
        customBanCommand  = config.getStringElse("ban-wave.custom-ban-command", "litebans:tempban {player} {duration} {reason}");

        if (dataFile != null) {
            scheduleAutoExecution();
        }
    }

    public boolean isEnabled()            { return enabled; }
    public boolean isQueueOnAutoPunish()  { return queueOnAutoPunish; }
    public int     getWaveNumber()        { return waveNumber; }
    public int     getQueueSize()         { return queue.size(); }

    public Map<UUID, BanEntry> getQueue() {
        return Collections.unmodifiableMap(queue);
    }

    public boolean isInQueue(UUID uuid) {
        return queue.containsKey(uuid);
    }

    public synchronized boolean addToQueue(UUID uuid, String name, String check, String addedBy) {
        return addToQueue(uuid, name, check, addedBy, null);
    }

    public synchronized boolean addToQueue(UUID uuid, String name, String check, String addedBy, @Nullable String duration) {
        if (queue.containsKey(uuid)) return false;
        queue.put(uuid, new BanEntry(uuid, name, check, addedBy, System.currentTimeMillis(), duration));
        save();
        return true;
    }

    public synchronized boolean removeFromQueue(UUID uuid) {
        if (!queue.containsKey(uuid)) return false;
        queue.remove(uuid);
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
        return MessageUtil.translateAlternateColorCodes('&', raw);
    }

    public String formatBanCommandReason(BanEntry entry, String waveStr) {
        return toBanCommandLine(formatKickReason(entry, waveStr));
    }

    public String formatEntry(int index, BanEntry entry) {
        String time = DATE_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()));
        String dur  = entry.duration() != null ? entry.duration() : "config default";
        return "  &8" + index + ". &f" + entry.name()
                + " &8│ &7check&8: &d" + entry.check()
                + " &8│ &7by&8: &f" + entry.addedBy()
                + " &8│ &7dur&8: &f" + dur
                + " &8│ &7added&8: &7" + time + " UTC";
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
        }
        long thresholdMs = (long) autoExecuteDays * 24L * 3600L * 1000L;
        if (System.currentTimeMillis() - lastAutoExecuteTime < thresholdMs) return;

        LogUtil.info("[BanWave] Auto-executing scheduled wave (interval: " + autoExecuteDays + "d)...");
        VoidAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(
                VoidAPI.INSTANCE.getVoidPlugin(),
                this::executeWave
        );
    }

    private void executeBan(BanEntry entry, String waveStr) {
        String effectiveDuration = entry.duration() != null ? entry.duration() : duration;
        String banId   = VoidAPI.INSTANCE.getPunishmentDatabase().reserveBanId();
        String dateStr = PunishmentDatabase.DATE_FORMAT_DISPLAY.format(Instant.now());

        String kickReason = formatKickReason(entry, waveStr)
                .replace("{ban_id}",  banId)
                .replace("{date}",    dateStr)
                .replace("{duration}", effectiveDuration);
        String banReason = toBanCommandLine(kickReason);

        VoidPlayer voidPlayer = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(entry.uuid());
        boolean isOffline = (voidPlayer == null || voidPlayer.platformPlayer == null);
        if (!isOffline) {
            final PlatformPlayer pp = voidPlayer.platformPlayer;
            VoidAPI.INSTANCE.getScheduler().getEntityScheduler().execute(
                    pp, VoidAPI.INSTANCE.getVoidPlugin(),
                    () -> pp.kickPlayer(kickReason), null, 0L
            );
        }

        if (preferCustomBan) {
            String cmd = customBanCommand
                    .replace("{player}",   entry.name())
                    .replace("{duration}", effectiveDuration)
                    .replace("{reason}",   banReason);
            dispatchCommand(cmd);
            // For offline targets, also store in VoidBanManager: external ban commands
            // (e.g. litebans:tempban) may not reliably apply when the player is offline.
            if (isOffline) {
                VoidAPI.INSTANCE.getVoidBanManager().ban(banId, entry.uuid(), entry.name(), kickReason, effectiveDuration, System.currentTimeMillis());
            }
        } else {
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

    private String toBanCommandLine(String richReason) {
        return richReason
                .replaceAll("[\\r\\n]+\\s*", " §8| §r")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private void dispatchCommand(String command) {
        VoidAPI.INSTANCE.getPlatformServer().dispatchCommand(
                VoidAPI.INSTANCE.getPlatformServer().getConsoleSender(), command
        );
    }

    private void load() {
        if (dataFile == null || !dataFile.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(dataFile))) {
            String line = r.readLine();
            if (line != null) waveNumber = Integer.parseInt(line.trim());

            line = r.readLine();
            if (line != null) lastAutoExecuteTime = Long.parseLong(line.trim());

            queue.clear();
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split("\\|", 6);
                if (p.length >= 4) {
                    UUID   uuid      = UUID.fromString(p[0]);
                    String name      = p[1];
                    String check     = p[2];
                    String addedBy   = p[3];
                    long   timestamp = p.length >= 5 ? Long.parseLong(p[4]) : System.currentTimeMillis();
                    String entryDur  = p.length >= 6 && !p[5].isBlank() ? p[5] : null;
                    queue.put(uuid, new BanEntry(uuid, name, check, addedBy, timestamp, entryDur));
                }
            }
            LogUtil.info("[BanWave] Loaded: " + queue.size() + " queued players (wave #" + waveNumber + ").");
        } catch (Exception e) {
            LogUtil.error("[BanWave] Failed to load banwave.dat", e);
        }
    }

    private synchronized void save() {
        if (dataFile == null) return;
        try {
            dataFile.getParentFile().mkdirs();
            try (PrintWriter w = new PrintWriter(new FileWriter(dataFile))) {
                w.println(waveNumber);
                w.println(lastAutoExecuteTime);
                for (BanEntry e : queue.values()) {
                    w.println(e.uuid() + "|" + e.name() + "|" + e.check()
                            + "|" + e.addedBy() + "|" + e.timestamp()
                            + "|" + (e.duration() != null ? e.duration() : ""));
                }
            }
        } catch (Exception e) {
            LogUtil.error("[BanWave] Failed to save banwave.dat", e);
        }
    }

    public record BanEntry(UUID uuid, String name, String check, String addedBy, long timestamp, @Nullable String duration) {
        public BanEntry(UUID uuid, String name, String check, String addedBy, long timestamp) {
            this(uuid, name, check, addedBy, timestamp, null);
        }
    }
}
