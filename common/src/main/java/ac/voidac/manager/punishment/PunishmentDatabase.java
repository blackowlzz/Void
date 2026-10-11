package ac.voidac.manager.punishment;

import ac.voidac.utils.anticheat.LogUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PunishmentDatabase {
    public static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    public static final DateTimeFormatter DATE_FORMAT_DISPLAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

    private static final String TABLE = "void_punishments";
    private static final String ACTIVE_BAN_COLUMNS = "uuid, player_name, reason, expires_at, ban_id, timestamp";

    private Connection connection;
    private long nextId = 1;

    public synchronized void init(File dataFolder) {
        dataFolder.mkdirs();
        File dbFile = new File(dataFolder, "punishments.db");
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            applyPragmas();
            createTables();
            nextId = readMaxId() + 1;
            LogUtil.info("[PunishmentDB] Initialized: " + (nextId - 1) + " existing records.");
        } catch (Exception e) {
            LogUtil.error("[PunishmentDB] Failed to initialize punishments.db", e);
        }
    }

    public synchronized boolean isReady() {
        return connection != null;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            LogUtil.warn("[PunishmentDB] Failed to close punishments.db: " + e.getMessage());
        }
    }

    public synchronized String reserveBanId() {
        return formatBanId(nextId++);
    }

    public synchronized void insertWithId(
            String banId,
            UUID uuid,
            String playerName,
            String type,
            String issuedBy,
            @Nullable String checkName,
            String reason,
            String duration,
            @Nullable Integer waveId,
            @Nullable String flagsSummary,
            long timestamp
    ) {
        if (connection == null) return;
        String sql = "INSERT INTO " + TABLE
                + " (ban_id, uuid, player_name, type, issued_by, check_name, reason, duration, wave_id, flags_summary, timestamp)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, banId);
            ps.setString(2, uuid.toString());
            ps.setString(3, playerName);
            ps.setString(4, type);
            ps.setString(5, issuedBy);
            ps.setString(6, checkName);
            ps.setString(7, reason);
            ps.setString(8, duration);
            if (waveId != null) ps.setInt(9, waveId); else ps.setNull(9, Types.INTEGER);
            ps.setString(10, flagsSummary);
            ps.setLong(11, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Failed to insert punishment for " + playerName, e);
        }
    }

    public synchronized List<PunishmentRecord> queryByName(String name) {
        return query("LOWER(player_name) = LOWER(?)", name);
    }

    public synchronized @Nullable PunishmentRecord queryByBanId(String banId) {
        List<PunishmentRecord> results = query("ban_id = ?", banId.toUpperCase(Locale.ROOT));
        return results.isEmpty() ? null : results.get(0);
    }

    public synchronized void activeBanInsert(String banId, UUID uuid, String playerName,
                                             String reason, long expiresAt, long timestamp) {
        if (connection == null) return;
        String sql = "INSERT OR REPLACE INTO void_active_bans (" + ACTIVE_BAN_COLUMNS + ") VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, reason);
            ps.setLong(4, expiresAt);
            ps.setString(5, banId);
            ps.setLong(6, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Failed to insert active ban for " + playerName, e);
        }
    }

    public synchronized boolean activeBanRemove(UUID uuid) {
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM void_active_bans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Failed to remove active ban for " + uuid, e);
            return false;
        }
    }

    public synchronized @Nullable UUID activeBanRemoveByName(String name) {
        ActiveBanRecord record = activeBanQueryByName(name);
        if (record == null) return null;
        activeBanRemove(record.uuid());
        return record.uuid();
    }

    public synchronized @Nullable ActiveBanRecord activeBanQuery(UUID uuid) {
        return querySingleActiveBan("uuid = ?", uuid.toString());
    }

    public synchronized @Nullable ActiveBanRecord activeBanQueryByName(String name) {
        return querySingleActiveBan("LOWER(player_name) = LOWER(?)", name);
    }

    public synchronized List<ActiveBanRecord> activeBanAll() {
        List<ActiveBanRecord> out = new ArrayList<>();
        if (connection == null) return out;
        try (PreparedStatement ps = connection.prepareStatement("SELECT " + ACTIVE_BAN_COLUMNS + " FROM void_active_bans");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(readActiveBan(rs));
            }
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Failed to list active bans", e);
        }
        return out;
    }

    public synchronized void activeBanCleanExpired() {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM void_active_bans WHERE expires_at > 0 AND expires_at < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            int removed = ps.executeUpdate();
            if (removed > 0) LogUtil.info("[PunishmentDB] Cleaned " + removed + " expired native ban(s).");
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Failed to clean expired bans", e);
        }
    }

    public synchronized void proxyBanMark(UUID uuid, String banId) {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO void_proxy_bans (uuid, ban_id) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, banId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Failed to mark proxy ban for " + uuid, e);
        }
    }

    public synchronized void proxyBanUnmark(UUID uuid) {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM void_proxy_bans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Failed to unmark proxy ban for " + uuid, e);
        }
    }

    public synchronized Map<UUID, String> proxyBanAll() {
        Map<UUID, String> out = new HashMap<>();
        if (connection == null) return out;
        try (PreparedStatement ps = connection.prepareStatement("SELECT uuid, ban_id FROM void_proxy_bans");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(UUID.fromString(rs.getString("uuid")), rs.getString("ban_id"));
            }
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Failed to list proxy bans", e);
        }
        return out;
    }

    private List<PunishmentRecord> query(String where, String param) {
        List<PunishmentRecord> out = new ArrayList<>();
        if (connection == null) return out;
        String sql = "SELECT id, ban_id, uuid, player_name, type, issued_by, check_name,"
                + " reason, duration, wave_id, flags_summary, timestamp"
                + " FROM " + TABLE + " WHERE " + where + " ORDER BY timestamp DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readPunishment(rs));
                }
            }
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Query failed (where=" + where + ")", e);
        }
        return out;
    }

    private @Nullable ActiveBanRecord querySingleActiveBan(String where, String param) {
        if (connection == null) return null;
        String sql = "SELECT " + ACTIVE_BAN_COLUMNS + " FROM void_active_bans WHERE " + where + " LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readActiveBan(rs) : null;
            }
        } catch (SQLException e) {
            LogUtil.error("[PunishmentDB] Active ban query failed (where=" + where + ", value=" + param + ")", e);
            return null;
        }
    }

    private static PunishmentRecord readPunishment(ResultSet rs) throws SQLException {
        int waveIdRaw = rs.getInt("wave_id");
        Integer waveId = rs.wasNull() ? null : waveIdRaw;
        return new PunishmentRecord(
                rs.getLong("id"),
                rs.getString("ban_id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                rs.getString("type"),
                rs.getString("issued_by"),
                rs.getString("check_name"),
                rs.getString("reason"),
                rs.getString("duration"),
                waveId,
                rs.getString("flags_summary"),
                rs.getLong("timestamp")
        );
    }

    private static ActiveBanRecord readActiveBan(ResultSet rs) throws SQLException {
        return new ActiveBanRecord(
                rs.getString("ban_id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                rs.getString("reason"),
                rs.getLong("expires_at"),
                rs.getLong("timestamp")
        );
    }

    private long readMaxId() throws SQLException {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(CAST(SUBSTR(ban_id, 6) AS INTEGER)), 0) FROM " + TABLE)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private void createTables() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "id            INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "ban_id        TEXT NOT NULL UNIQUE,"
                    + "uuid          TEXT NOT NULL,"
                    + "player_name   TEXT NOT NULL,"
                    + "type          TEXT NOT NULL,"
                    + "issued_by     TEXT NOT NULL,"
                    + "check_name    TEXT,"
                    + "reason        TEXT,"
                    + "duration      TEXT NOT NULL DEFAULT 'permanent',"
                    + "wave_id       INTEGER,"
                    + "flags_summary TEXT,"
                    + "timestamp     INTEGER NOT NULL"
                    + ")");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON " + TABLE + "(uuid)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_punishments_name ON " + TABLE + "(LOWER(player_name))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS void_active_bans ("
                    + "uuid        TEXT PRIMARY KEY,"
                    + "player_name TEXT NOT NULL,"
                    + "reason      TEXT NOT NULL,"
                    + "expires_at  INTEGER NOT NULL,"
                    + "ban_id      TEXT NOT NULL,"
                    + "timestamp   INTEGER NOT NULL"
                    + ")");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_active_bans_name ON void_active_bans(LOWER(player_name))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS void_proxy_bans ("
                    + "uuid   TEXT PRIMARY KEY,"
                    + "ban_id TEXT NOT NULL"
                    + ")");
        }
    }

    private void applyPragmas() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA busy_timeout=5000");
        }
    }

    private static String formatBanId(long id) {
        return String.format(Locale.ROOT, "VOID-%06d", id);
    }
}
