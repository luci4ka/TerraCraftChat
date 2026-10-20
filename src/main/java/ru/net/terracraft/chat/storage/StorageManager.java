package ru.net.terracraft.chat.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StorageManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private boolean useMySQL;
    private String tablePrefix;

    private ExecutorService asyncExecutor;
    private final ConcurrentLinkedQueue<CompletableFuture<?>> pending = new ConcurrentLinkedQueue<>();

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        var storage = ((TerraCraftChatPlugin) plugin).getChatConfig().storage();
        this.useMySQL = "mysql".equalsIgnoreCase(storage.type());
        this.tablePrefix = storage.tablePrefix();

        if (!this.tablePrefix.matches("[a-zA-Z0-9_]*")) {
            plugin.getLogger().warning("Storage: некорректный tablePrefix '" + tablePrefix
                    + "', сбрасываю в 'tc_'");
            this.tablePrefix = "tc_";
        }

        try {
            HikariConfig hikari = new HikariConfig();

            if (useMySQL) {
                var m = storage.mysql();
                hikari.setJdbcUrl("jdbc:mysql://" + m.host() + ":" + m.port() + "/" + m.database()
                        + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
                hikari.setUsername(m.username());
                hikari.setPassword(m.password());
                hikari.setMaximumPoolSize(m.poolSize());
                hikari.setPoolName("TerraCraftChat-MySQL");
            } else {
                String file = storage.h2().file();
                String path = new java.io.File(plugin.getDataFolder(), file).getAbsolutePath();
                hikari.setJdbcUrl("jdbc:h2:" + path + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
                hikari.setDriverClassName("org.h2.Driver");
                hikari.setMaximumPoolSize(1);
                hikari.setPoolName("TerraCraftChat-H2");
            }

            this.dataSource = new HikariDataSource(hikari);
            createTables();

            this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "TerraCraftChat-Storage");
                t.setDaemon(true);
                return t;
            });

            plugin.getLogger().info("Storage: " + (useMySQL ? "MySQL" : "H2") + " подключён.");
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось подключить БД: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "ignores (" +
                    "viewer VARCHAR(36) NOT NULL, " +
                    "target VARCHAR(36) NOT NULL, " +
                    "expires_at BIGINT NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (viewer, target))");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "message_history (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "sender_uuid VARCHAR(36) NOT NULL, " +
                    "sender_name VARCHAR(32) NOT NULL, " +
                    "channel_id VARCHAR(32) NOT NULL, " +
                    "raw_message TEXT NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "INDEX idx_" + tablePrefix + "history_ts (timestamp))");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "nicks (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "nick TEXT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "socialspy (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "enabled BOOLEAN NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "replies (" +
                    "viewer VARCHAR(36) PRIMARY KEY, " +
                    "target VARCHAR(36) NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "channels (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "channel_id VARCHAR(32) NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "rules_log (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "rule_id VARCHAR(64) NOT NULL, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "player_name VARCHAR(32) NOT NULL, " +
                    "message TEXT, " +
                    "timestamp BIGINT NOT NULL, " +
                    "INDEX idx_" + tablePrefix + "rules_ts (timestamp))");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_stats (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "messages_sent BIGINT NOT NULL DEFAULT 0, " +
                    "messages_blocked BIGINT NOT NULL DEFAULT 0, " +
                    "swear_caught BIGINT NOT NULL DEFAULT 0, " +
                    "ads_caught BIGINT NOT NULL DEFAULT 0)");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "thread_replies (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "parent_id VARCHAR(36) NOT NULL, " +
                    "author_uuid VARCHAR(36) NOT NULL, " +
                    "author_name VARCHAR(32) NOT NULL, " +
                    "text TEXT NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "INDEX idx_" + tablePrefix + "thread_parent (parent_id))");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "message_viewers (" +
                    "message_id VARCHAR(36) NOT NULL, " +
                    "viewer_uuid VARCHAR(36) NOT NULL, " +
                    "PRIMARY KEY (message_id, viewer_uuid), " +
                    "INDEX idx_" + tablePrefix + "viewers_msg (message_id))");

            // === Групповые чаты ===
            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "chat_groups (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(32) NOT NULL UNIQUE, " +
                    "owner_uuid VARCHAR(36) NOT NULL, " +
                    "created_at BIGINT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "chat_group_members (" +
                    "group_id VARCHAR(36) NOT NULL, " +
                    "member_uuid VARCHAR(36) NOT NULL, " +
                    "PRIMARY KEY (group_id, member_uuid), " +
                    "INDEX idx_" + tablePrefix + "group_members (group_id))");

            st.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "chat_group_history (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "group_id VARCHAR(36) NOT NULL, " +
                    "sender_uuid VARCHAR(36) NOT NULL, " +
                    "sender_name VARCHAR(32) NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "INDEX idx_" + tablePrefix + "group_history (group_id, timestamp))");
        }
    }

    private void async(Runnable task) {
        if (asyncExecutor == null) {
            task.run();
            return;
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().warning("Storage async task failed: " + e.getMessage());
            }
        }, asyncExecutor);
        pending.add(future);
        pending.removeIf(CompletableFuture::isDone);
    }

    // ===== Ignore =====

    public void addIgnore(UUID viewer, UUID target, long expiresAtMs) {
        async(() -> addIgnoreSync(viewer, target, expiresAtMs));
    }

    private void addIgnoreSync(UUID viewer, UUID target, long expiresAtMs) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + tablePrefix + "ignores (viewer, target, expires_at) VALUES (?, ?, ?)")) {
            ps.setString(1, viewer.toString());
            ps.setString(2, target.toString());
            ps.setLong(3, expiresAtMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB addIgnore: " + e.getMessage());
        }
    }

    public void addIgnore(UUID viewer, UUID target) {
        addIgnore(viewer, target, 0L);
    }

    public void removeIgnore(UUID viewer, UUID target) {
        async(() -> removeIgnoreSync(viewer, target));
    }

    private void removeIgnoreSync(UUID viewer, UUID target) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + tablePrefix + "ignores WHERE viewer = ? AND target = ?")) {
            ps.setString(1, viewer.toString());
            ps.setString(2, target.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB removeIgnore: " + e.getMessage());
        }
    }

    public Set<UUID> getIgnores(UUID viewer) {
        Set<UUID> result = new HashSet<>();
        if (dataSource == null) return result;

        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT target, expires_at FROM " + tablePrefix + "ignores WHERE viewer = ?")) {
            ps.setString(1, viewer.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID target = UUID.fromString(rs.getString("target"));
                        long expiresAt = rs.getLong("expires_at");
                        if (expiresAt > 0 && now > expiresAt) {
                            expired.add(target);
                        } else {
                            result.add(target);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getIgnores: " + e.getMessage());
        }

        for (UUID target : expired) {
            removeIgnore(viewer, target);
        }
        return result;
    }

    public void cleanupExpiredIgnores(long nowMs) {
        async(() -> {
            if (dataSource == null) return;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM " + tablePrefix + "ignores WHERE expires_at > 0 AND expires_at < ?")) {
                ps.setLong(1, nowMs);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB cleanupExpiredIgnores: " + e.getMessage());
            }
        });
    }

    // ===== History =====

    public void saveMessage(UUID id, UUID senderUuid, String senderName,
                            String channelId, String rawMessage, long timestamp) {
        async(() -> saveMessageSync(id, senderUuid, senderName, channelId, rawMessage, timestamp));
    }

    private void saveMessageSync(UUID id, UUID senderUuid, String senderName,
                                 String channelId, String rawMessage, long timestamp) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + tablePrefix + "message_history " +
                             "(id, sender_uuid, sender_name, channel_id, raw_message, timestamp) " +
                             "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id.toString());
            ps.setString(2, senderUuid.toString());
            ps.setString(3, senderName);
            ps.setString(4, channelId);
            ps.setString(5, rawMessage);
            ps.setLong(6, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB saveMessage: " + e.getMessage());
        }
    }

    public void deleteMessage(UUID id) {
        async(() -> deleteMessageSync(id));
    }

    private void deleteMessageSync(UUID id) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + tablePrefix + "message_viewers WHERE message_id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + tablePrefix + "thread_replies WHERE parent_id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + tablePrefix + "message_history WHERE id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB deleteMessage: " + e.getMessage());
        }
    }

    public void cleanupHistory(long olderThanMs) {
        async(() -> cleanupHistorySync(olderThanMs));
    }

    private void cleanupHistorySync(long olderThanMs) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + tablePrefix + "message_viewers WHERE message_id IN " +
                            "(SELECT id FROM " + tablePrefix + "message_history WHERE timestamp < ?)")) {
                ps.setLong(1, olderThanMs);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + tablePrefix + "thread_replies WHERE parent_id IN " +
                            "(SELECT id FROM " + tablePrefix + "message_history WHERE timestamp < ?)")) {
                ps.setLong(1, olderThanMs);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + tablePrefix + "message_history WHERE timestamp < ?")) {
                ps.setLong(1, olderThanMs);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB cleanupHistory: " + e.getMessage());
        }
    }

    // ===== Message viewers =====

    public void saveMessageViewers(UUID messageId, Set<UUID> viewers) {
        if (viewers.isEmpty()) return;
        async(() -> saveMessageViewersSync(messageId, viewers));
    }

    private void saveMessageViewersSync(UUID messageId, Set<UUID> viewers) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + tablePrefix + "message_viewers (message_id, viewer_uuid) VALUES (?, ?)")) {
            for (UUID viewer : viewers) {
                ps.setString(1, messageId.toString());
                ps.setString(2, viewer.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB saveMessageViewers: " + e.getMessage());
        }
    }

    public List<UUID> loadViewers(UUID messageId) {
        List<UUID> result = new ArrayList<>();
        if (dataSource == null) return result;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT viewer_uuid FROM " + tablePrefix + "message_viewers WHERE message_id = ?")) {
            ps.setString(1, messageId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try { result.add(UUID.fromString(rs.getString("viewer_uuid"))); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadViewers: " + e.getMessage());
        }
        return result;
    }

    public Map<UUID, List<UUID>> loadViewersBatch(List<UUID> messageIds) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        if (dataSource == null || messageIds.isEmpty()) return result;

        String placeholders = String.join(",", Collections.nCopies(messageIds.size(), "?"));
        String sql = "SELECT message_id, viewer_uuid FROM " + tablePrefix
                + "message_viewers WHERE message_id IN (" + placeholders + ")";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < messageIds.size(); i++) {
                ps.setString(i + 1, messageIds.get(i).toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID msgId = UUID.fromString(rs.getString("message_id"));
                        UUID viewerId = UUID.fromString(rs.getString("viewer_uuid"));
                        result.computeIfAbsent(msgId, k -> new ArrayList<>()).add(viewerId);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadViewersBatch: " + e.getMessage());
        }
        return result;
    }

    public List<HistoryRow> loadRecentHistory(long cutoffMs, int limit) {
        List<HistoryRow> result = new ArrayList<>();
        if (dataSource == null) return result;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, sender_uuid, sender_name, channel_id, raw_message, timestamp " +
                             "FROM " + tablePrefix + "message_history " +
                             "WHERE timestamp >= ? ORDER BY timestamp ASC LIMIT ?")) {
            ps.setLong(1, cutoffMs);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.add(new HistoryRow(
                                UUID.fromString(rs.getString("id")),
                                UUID.fromString(rs.getString("sender_uuid")),
                                rs.getString("sender_name"),
                                rs.getString("channel_id"),
                                rs.getString("raw_message"),
                                rs.getLong("timestamp")
                        ));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadRecentHistory: " + e.getMessage());
        }
        return result;
    }

    public List<HistoryRow> loadHistoryBySender(UUID senderUuid, int limit) {
        List<HistoryRow> result = new ArrayList<>();
        if (dataSource == null) return result;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, sender_uuid, sender_name, channel_id, raw_message, timestamp " +
                             "FROM " + tablePrefix + "message_history " +
                             "WHERE sender_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            ps.setString(1, senderUuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.add(new HistoryRow(
                                UUID.fromString(rs.getString("id")),
                                UUID.fromString(rs.getString("sender_uuid")),
                                rs.getString("sender_name"),
                                rs.getString("channel_id"),
                                rs.getString("raw_message"),
                                rs.getLong("timestamp")
                        ));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadHistoryBySender: " + e.getMessage());
        }
        return result;
    }

    public record HistoryRow(UUID id, UUID senderUuid, String senderName,
                             String channelId, String rawMessage, long timestamp) {}

    // ===== Ники =====

    public void setNick(UUID uuid, String nick) {
        async(() -> setNickSync(uuid, nick));
    }

    private void setNickSync(UUID uuid, String nick) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + tablePrefix + "nicks (uuid, nick) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, nick);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB setNick: " + e.getMessage());
        }
    }

    public String getNick(UUID uuid) {
        if (dataSource == null) return null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nick FROM " + tablePrefix + "nicks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("nick");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getNick: " + e.getMessage());
        }
        return null;
    }

    public void removeNick(UUID uuid) {
        async(() -> removeNickSync(uuid));
    }

    private void removeNickSync(UUID uuid) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + tablePrefix + "nicks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB removeNick: " + e.getMessage());
        }
    }

    public Map<UUID, String> getAllNicks() {
        Map<UUID, String> result = new HashMap<>();
        if (dataSource == null) return result;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid, nick FROM " + tablePrefix + "nicks")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.put(UUID.fromString(rs.getString("uuid")), rs.getString("nick"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getAllNicks: " + e.getMessage());
        }
        return result;
    }

    // ===== Соц-шпион =====

    public void setSocialSpy(UUID uuid, boolean enabled) {
        async(() -> setSocialSpySync(uuid, enabled));
    }

    private void setSocialSpySync(UUID uuid, boolean enabled) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + tablePrefix + "socialspy (uuid, enabled) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setBoolean(2, enabled);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB setSocialSpy: " + e.getMessage());
        }
    }

    public boolean isSocialSpy(UUID uuid) {
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT enabled FROM " + tablePrefix + "socialspy WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBoolean("enabled");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB isSocialSpy: " + e.getMessage());
        }
        return false;
    }

    public Set<UUID> getAllSocialSpy() {
        Set<UUID> result = new HashSet<>();
        if (dataSource == null) return result;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid FROM " + tablePrefix + "socialspy WHERE enabled = TRUE")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try { result.add(UUID.fromString(rs.getString("uuid"))); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getAllSocialSpy: " + e.getMessage());
        }
        return result;
    }

    // ===== Reply =====

    public void setReply(UUID viewer, UUID target) {
        async(() -> setReplySync(viewer, target));
    }

    private void setReplySync(UUID viewer, UUID target) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + tablePrefix + "replies (viewer, target) VALUES (?, ?)")) {
            ps.setString(1, viewer.toString());
            ps.setString(2, target.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB setReply: " + e.getMessage());
        }
    }

    public UUID getReply(UUID viewer) {
        if (dataSource == null) return null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT target FROM " + tablePrefix + "replies WHERE viewer = ?")) {
            ps.setString(1, viewer.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("target"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getReply: " + e.getMessage());
        }
        return null;
    }

    public void clearReply(UUID viewer) {
        async(() -> clearReplySync(viewer));
    }

    private void clearReplySync(UUID viewer) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + tablePrefix + "replies WHERE viewer = ?")) {
            ps.setString(1, viewer.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB clearReply: " + e.getMessage());
        }
    }

    // ===== Активный канал =====

    public void setActiveChannel(UUID uuid, String channelId) {
        async(() -> setActiveChannelSync(uuid, channelId));
    }

    private void setActiveChannelSync(UUID uuid, String channelId) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + tablePrefix + "channels (uuid, channel_id) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, channelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB setActiveChannel: " + e.getMessage());
        }
    }

    public String getActiveChannel(UUID uuid) {
        if (dataSource == null) return null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT channel_id FROM " + tablePrefix + "channels WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("channel_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getActiveChannel: " + e.getMessage());
        }
        return null;
    }

    public void clearActiveChannel(UUID uuid) {
        async(() -> clearActiveChannelSync(uuid));
    }

    private void clearActiveChannelSync(UUID uuid) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + tablePrefix + "channels WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB clearActiveChannel: " + e.getMessage());
        }
    }

    // ===== Аудит правил =====

    public void logRule(String ruleId, UUID playerUuid, String playerName,
                        String message, long timestamp) {
        async(() -> logRuleSync(ruleId, playerUuid, playerName, message, timestamp));
    }

    private void logRuleSync(String ruleId, UUID playerUuid, String playerName,
                             String message, long timestamp) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "rules_log " +
                             "(rule_id, player_uuid, player_name, message, timestamp) " +
                             "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, ruleId);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, playerName);
            ps.setString(4, message);
            ps.setLong(5, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB logRule: " + e.getMessage());
        }
    }

    // ===== Статистика =====

    public void incrementStat(UUID uuid, String column) {
        async(() -> incrementStatSync(uuid, column));
    }

    private void incrementStatSync(UUID uuid, String column) {
        if (dataSource == null) return;
        switch (column) {
            case "messages_sent", "messages_blocked", "swear_caught", "ads_caught" -> {}
            default -> { return; }
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE " + tablePrefix + "player_stats SET " + column + " = " + column + " + 1 " +
                             "WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            if (ps.executeUpdate() > 0) return;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB incrementStat(update): " + e.getMessage());
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "player_stats (uuid, " + column + ") VALUES (?, 1)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE " + tablePrefix + "player_stats SET " + column + " = " + column + " + 1 " +
                                 "WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e2) {
                plugin.getLogger().warning("DB incrementStat(insert/retry): " + e2.getMessage());
            }
        }
    }

    public PlayerStats getPlayerStats(UUID uuid) {
        if (dataSource == null) return new PlayerStats(0, 0, 0, 0);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT messages_sent, messages_blocked, swear_caught, ads_caught " +
                             "FROM " + tablePrefix + "player_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(
                            rs.getLong("messages_sent"),
                            rs.getLong("messages_blocked"),
                            rs.getLong("swear_caught"),
                            rs.getLong("ads_caught")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getPlayerStats: " + e.getMessage());
        }
        return new PlayerStats(0, 0, 0, 0);
    }

    public record PlayerStats(long messagesSent, long messagesBlocked,
                              long swearCaught, long adsCaught) {}

    public void purgeHistory(long olderThanMs) {
        async(() -> cleanupHistorySync(olderThanMs));
    }

    // ===== Thread replies =====

    public void saveThreadReply(UUID id, UUID parentId, UUID authorUuid,
                                String authorName, String text, long timestamp) {
        async(() -> saveThreadReplySync(id, parentId, authorUuid, authorName, text, timestamp));
    }

    private void saveThreadReplySync(UUID id, UUID parentId, UUID authorUuid,
                                     String authorName, String text, long timestamp) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO " + tablePrefix + "thread_replies " +
                             "(id, parent_id, author_uuid, author_name, text, timestamp) " +
                             "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id.toString());
            ps.setString(2, parentId.toString());
            ps.setString(3, authorUuid.toString());
            ps.setString(4, authorName);
            ps.setString(5, text);
            ps.setLong(6, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB saveThreadReply: " + e.getMessage());
        }
    }

    public List<ThreadReplyRow> getThreadReplies(UUID parentId) {
        List<ThreadReplyRow> result = new ArrayList<>();
        if (dataSource == null) return result;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, author_uuid, author_name, text, timestamp " +
                             "FROM " + tablePrefix + "thread_replies " +
                             "WHERE parent_id = ? ORDER BY timestamp ASC")) {
            ps.setString(1, parentId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.add(new ThreadReplyRow(
                                UUID.fromString(rs.getString("id")),
                                UUID.fromString(rs.getString("author_uuid")),
                                rs.getString("author_name"),
                                rs.getString("text"),
                                rs.getLong("timestamp")
                        ));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getThreadReplies: " + e.getMessage());
        }
        return result;
    }

    public Map<UUID, List<ThreadReplyRow>> getThreadRepliesBatch(List<UUID> parentIds) {
        Map<UUID, List<ThreadReplyRow>> result = new HashMap<>();
        if (dataSource == null || parentIds.isEmpty()) return result;

        String placeholders = String.join(",", Collections.nCopies(parentIds.size(), "?"));
        String sql = "SELECT parent_id, id, author_uuid, author_name, text, timestamp "
                + "FROM " + tablePrefix + "thread_replies "
                + "WHERE parent_id IN (" + placeholders + ") ORDER BY timestamp ASC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < parentIds.size(); i++) {
                ps.setString(i + 1, parentIds.get(i).toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID parentId = UUID.fromString(rs.getString("parent_id"));
                        result.computeIfAbsent(parentId, k -> new ArrayList<>()).add(
                                new ThreadReplyRow(
                                        UUID.fromString(rs.getString("id")),
                                        UUID.fromString(rs.getString("author_uuid")),
                                        rs.getString("author_name"),
                                        rs.getString("text"),
                                        rs.getLong("timestamp")
                                )
                        );
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getThreadRepliesBatch: " + e.getMessage());
        }
        return result;
    }

    public void deleteThreadReplies(UUID parentId) {
        async(() -> deleteThreadRepliesSync(parentId));
    }

    private void deleteThreadRepliesSync(UUID parentId) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + tablePrefix + "thread_replies WHERE parent_id = ?")) {
            ps.setString(1, parentId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB deleteThreadReplies: " + e.getMessage());
        }
    }

    public record ThreadReplyRow(UUID id, UUID authorUuid, String authorName,
                                 String text, long timestamp) {}

    // ===== Group chats =====

    public UUID createGroup(String name, UUID ownerUuid) {
        if (dataSource == null) return null;
        UUID id = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + tablePrefix + "chat_groups (id, name, owner_uuid, created_at) " +
                             "VALUES (?, ?, ?, ?)")) {
            ps.setString(1, id.toString());
            ps.setString(2, name);
            ps.setString(3, ownerUuid.toString());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB createGroup: " + e.getMessage());
            return null;
        }
    }

    public boolean addGroupMember(UUID groupId, UUID memberUuid) {
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT IGNORE INTO " + tablePrefix + "chat_group_members (group_id, member_uuid) " +
                             "VALUES (?, ?)")) {
            ps.setString(1, groupId.toString());
            ps.setString(2, memberUuid.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB addGroupMember: " + e.getMessage());
            return false;
        }
    }

    public boolean removeGroupMember(UUID groupId, UUID memberUuid) {
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + tablePrefix + "chat_group_members " +
                             "WHERE group_id = ? AND member_uuid = ?")) {
            ps.setString(1, groupId.toString());
            ps.setString(2, memberUuid.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB removeGroupMember: " + e.getMessage());
            return false;
        }
    }

    public List<UUID> getGroupMembers(UUID groupId) {
        List<UUID> result = new ArrayList<>();
        if (dataSource == null) return result;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT member_uuid FROM " + tablePrefix + "chat_group_members WHERE group_id = ?")) {
            ps.setString(1, groupId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try { result.add(UUID.fromString(rs.getString("member_uuid"))); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getGroupMembers: " + e.getMessage());
        }
        return result;
    }

    public GroupRow getGroupByName(String name) {
        if (dataSource == null) return null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, owner_uuid, created_at FROM " + tablePrefix + "chat_groups WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GroupRow(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("name"),
                            UUID.fromString(rs.getString("owner_uuid")),
                            rs.getLong("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getGroupByName: " + e.getMessage());
        }
        return null;
    }

    public void deleteGroup(UUID groupId) {
        async(() -> {
            if (dataSource == null) return;
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + tablePrefix + "chat_group_history WHERE group_id = ?")) {
                    ps.setString(1, groupId.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + tablePrefix + "chat_group_members WHERE group_id = ?")) {
                    ps.setString(1, groupId.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + tablePrefix + "chat_groups WHERE id = ?")) {
                    ps.setString(1, groupId.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("DB deleteGroup: " + e.getMessage());
            }
        });
    }

    public void saveGroupMessage(UUID id, UUID groupId, UUID senderUuid,
                                 String senderName, String message, long timestamp) {
        async(() -> {
            if (dataSource == null) return;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO " + tablePrefix + "chat_group_history " +
                                 "(id, group_id, sender_uuid, sender_name, message, timestamp) " +
                                 "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, groupId.toString());
                ps.setString(3, senderUuid.toString());
                ps.setString(4, senderName);
                ps.setString(5, message);
                ps.setLong(6, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB saveGroupMessage: " + e.getMessage());
            }
        });
    }

    public record GroupRow(UUID id, String name, UUID ownerUuid, long createdAt) {}

    // ===== CLOSE =====

    public void close() {
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Storage: не все записи завершились за 5 сек, "
                            + "возможна потеря последних сообщений.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getLogger().warning("Storage: прервано ожидание записей.");
            }
            asyncExecutor = null;
        }

        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}