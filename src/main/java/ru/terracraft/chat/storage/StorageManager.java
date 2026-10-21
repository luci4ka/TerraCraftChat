package ru.terracraft.chat.storage;

import cc.carm.lib.easysql.api.SQLManager;
import cc.carm.lib.easysql.api.SQLQuery;
import cc.carm.plugin.minesql.MineSQL;
import org.bukkit.plugin.java.JavaPlugin;
import ru.terracraft.chat.TerraCraftChatPlugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

    public static final String SOURCE_NAME = "terracraft-chat";

    private final JavaPlugin plugin;
    private SQLManager sqlManager;
    private String tablePrefix;

    private ExecutorService asyncExecutor;
    private final ConcurrentLinkedQueue<CompletableFuture<?>> pending = new ConcurrentLinkedQueue<>();

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        var storage = ((TerraCraftChatPlugin) plugin).getChatConfig().storage();
        this.tablePrefix = storage.tablePrefix();

        if (!this.tablePrefix.matches("[a-zA-Z0-9_]*")) {
            plugin.getLogger().warning("Storage: некорректный tablePrefix '" + tablePrefix
                    + "', сбрасываю в 'tc_'");
            this.tablePrefix = "tc_";
        }

        if (plugin.getServer().getPluginManager().getPlugin("MineSQL") == null) {
            plugin.getLogger().info("MineSQL not found — storage disabled.");
            return;
        }

        try {
            this.sqlManager = MineSQL.getRegistry().get(SOURCE_NAME);
            if (sqlManager == null) {
                plugin.getLogger().severe("MineSQL source '" + SOURCE_NAME + "' not found!");
                plugin.getLogger().severe("Add it to plugins/MineSQL/config.yml");
                return;
            }
            plugin.getLogger().info("Storage connected: MineSQL (" + SOURCE_NAME + ")");
            migrate();
        } catch (Exception e) {
            plugin.getLogger().severe("Storage init failed: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TerraCraftChat-Storage");
            t.setDaemon(true);
            return t;
        });
    }

    private void migrate() {
        String p = tablePrefix;

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "ignores (" +
                "viewer VARCHAR(36) NOT NULL, " +
                "target VARCHAR(36) NOT NULL, " +
                "expires_at BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (viewer, target))", "ignores");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "message_history (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "sender_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(32) NOT NULL, " +
                "channel_id VARCHAR(32) NOT NULL, " +
                "raw_message TEXT NOT NULL, " +
                "timestamp BIGINT NOT NULL)", "message_history");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "nicks (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "nick TEXT NOT NULL)", "nicks");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "socialspy (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "enabled BOOLEAN NOT NULL)", "socialspy");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "replies (" +
                "viewer VARCHAR(36) PRIMARY KEY, " +
                "target VARCHAR(36) NOT NULL)", "replies");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "channels (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "channel_id VARCHAR(32) NOT NULL)", "channels");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "rules_log (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "rule_id VARCHAR(64) NOT NULL, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(32) NOT NULL, " +
                "message TEXT, " +
                "timestamp BIGINT NOT NULL)", "rules_log");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "player_stats (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "messages_sent BIGINT NOT NULL DEFAULT 0, " +
                "messages_blocked BIGINT NOT NULL DEFAULT 0, " +
                "swear_caught BIGINT NOT NULL DEFAULT 0, " +
                "ads_caught BIGINT NOT NULL DEFAULT 0)", "player_stats");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "thread_replies (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "parent_id VARCHAR(36) NOT NULL, " +
                "author_uuid VARCHAR(36) NOT NULL, " +
                "author_name VARCHAR(32) NOT NULL, " +
                "text TEXT NOT NULL, " +
                "timestamp BIGINT NOT NULL)", "thread_replies");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "message_viewers (" +
                "message_id VARCHAR(36) NOT NULL, " +
                "viewer_uuid VARCHAR(36) NOT NULL, " +
                "PRIMARY KEY (message_id, viewer_uuid))", "message_viewers");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "chat_groups (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(32) NOT NULL UNIQUE, " +
                "owner_uuid VARCHAR(36) NOT NULL, " +
                "created_at BIGINT NOT NULL)", "chat_groups");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "chat_group_members (" +
                "group_id VARCHAR(36) NOT NULL, " +
                "member_uuid VARCHAR(36) NOT NULL, " +
                "PRIMARY KEY (group_id, member_uuid))", "chat_group_members");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "chat_group_history (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "group_id VARCHAR(36) NOT NULL, " +
                "sender_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(32) NOT NULL, " +
                "message TEXT NOT NULL, " +
                "timestamp BIGINT NOT NULL)", "chat_group_history");

        // === Индексы (отдельно, безопасно) ===
        createIndex("idx_" + p + "history_ts", p + "message_history", "timestamp");
        createIndex("idx_" + p + "rules_ts", p + "rules_log", "timestamp");
        createIndex("idx_" + p + "thread_parent", p + "thread_replies", "parent_id");
        createIndex("idx_" + p + "viewers_msg", p + "message_viewers", "message_id");
        createIndex("idx_" + p + "group_members", p + "chat_group_members", "group_id");
        createIndex("idx_" + p + "group_history", p + "chat_group_history", "group_id, timestamp");
    }

    private void executeRaw(String sql, String name) {
        try {
            sql().executeSQL(sql);
            plugin.getLogger().info("Table ready: " + name);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create table " + name + ": " + e.getMessage());
        }
    }

    private void createIndex(String indexName, String table, String columns) {
        try {
            sql().executeSQL("CREATE INDEX " + indexName + " ON " + table + "(" + columns + ")");
        } catch (Exception ignored) {}
    }

    private SQLManager sql() {
        if (sqlManager == null) {
            throw new IllegalStateException("StorageManager not initialized (MineSQL missing)");
        }
        return sqlManager;
    }

    public SQLManager sqlOrNull() {
        return sqlManager;
    }

    private void async(Runnable task) {
        if (asyncExecutor == null) {
            task.run();
            return;
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try { task.run(); }
            catch (Exception e) { plugin.getLogger().warning("Storage async task failed: " + e.getMessage()); }
        }, asyncExecutor);
        pending.add(future);
        pending.removeIf(CompletableFuture::isDone);
    }

    // ============================================================
    //  Ignore
    // ============================================================

    public void addIgnore(UUID viewer, UUID target, long expiresAtMs) {
        sql().createReplace(tablePrefix + "ignores")
                .setColumnNames("viewer", "target", "expires_at")
                .setParams(viewer.toString(), target.toString(), expiresAtMs)
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB addIgnore: " + exception.getMessage())
                );
    }

    public void addIgnore(UUID viewer, UUID target) {
        addIgnore(viewer, target, 0L);
    }

    public void removeIgnore(UUID viewer, UUID target) {
        sql().createDelete(tablePrefix + "ignores")
                .addCondition("viewer", viewer.toString())
                .addCondition("target", target.toString())
                .build()
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB removeIgnore: " + exception.getMessage())
                );
    }

    public Set<UUID> getIgnores(UUID viewer) {
        Set<UUID> result = new HashSet<>();
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "ignores")
                .selectColumns("target", "expires_at")
                .addCondition("viewer", viewer.toString())
                .build()
                .execute()) {

            ResultSet rs = query.getResultSet();
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
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getIgnores: " + e.getMessage());
        }

        for (UUID target : expired) {
            removeIgnore(viewer, target);
        }
        return result;
    }

    public void cleanupExpiredIgnores(long nowMs) {
        sql().createDelete(tablePrefix + "ignores")
                .addCondition("expires_at", ">", 0)
                .addCondition("expires_at", "<", nowMs)
                .build()
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB cleanupExpiredIgnores: " + exception.getMessage())
                );
    }

    // ============================================================
    //  History
    // ============================================================

    public void saveMessage(UUID id, UUID senderUuid, String senderName,
                            String channelId, String rawMessage, long timestamp) {
        sql().createReplace(tablePrefix + "message_history")
                .setColumnNames("id", "sender_uuid", "sender_name", "channel_id", "raw_message", "timestamp")
                .setParams(id.toString(), senderUuid.toString(), senderName, channelId, rawMessage, timestamp)
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB saveMessage: " + exception.getMessage())
                );
    }

    public void deleteMessage(UUID id) {
        async(() -> {
            try {
                sql().createDelete(tablePrefix + "message_viewers")
                        .addCondition("message_id", id.toString())
                        .build().execute();
                sql().createDelete(tablePrefix + "thread_replies")
                        .addCondition("parent_id", id.toString())
                        .build().execute();
                sql().createDelete(tablePrefix + "message_history")
                        .addCondition("id", id.toString())
                        .build().execute();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB deleteMessage: " + e.getMessage());
            }
        });
    }

    public void cleanupHistory(long olderThanMs) {
        async(() -> {
            try {
                sql().createDelete(tablePrefix + "message_viewers")
                        .addCondition("message_id", "IN",
                                "(SELECT id FROM " + tablePrefix + "message_history WHERE timestamp < " + olderThanMs + ")")
                        .build().execute();
                sql().createDelete(tablePrefix + "thread_replies")
                        .addCondition("parent_id", "IN",
                                "(SELECT id FROM " + tablePrefix + "message_history WHERE timestamp < " + olderThanMs + ")")
                        .build().execute();
                sql().createDelete(tablePrefix + "message_history")
                        .addCondition("timestamp", "<", olderThanMs)
                        .build().execute();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB cleanupHistory: " + e.getMessage());
            }
        });
    }

    public void purgeHistory(long olderThanMs) {
        cleanupHistory(olderThanMs);
    }

    // ============================================================
    //  Message viewers — BATCH
    // ============================================================

    public void saveMessageViewers(UUID messageId, Set<UUID> viewers) {
        if (viewers.isEmpty()) return;

        Set<UUID> snapshot = Set.copyOf(viewers);

        async(() -> {
            try {
                StringBuilder sql = new StringBuilder(
                        "INSERT INTO " + tablePrefix + "message_viewers (message_id, viewer_uuid) VALUES ");
                Object[] params = new Object[snapshot.size() * 2];
                int i = 0;
                boolean first = true;
                for (UUID viewer : snapshot) {
                    if (!first) sql.append(",");
                    first = false;
                    sql.append("(?, ?)");
                    params[i++] = messageId.toString();
                    params[i++] = viewer.toString();
                }
                sql().executeSQL(sql.toString(), params);
            } catch (Exception e) {
                plugin.getLogger().warning("DB saveMessageViewers: " + e.getMessage());
            }
        });
    }

    public List<UUID> loadViewers(UUID messageId) {
        List<UUID> result = new ArrayList<>();
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "message_viewers")
                .selectColumns("viewer_uuid")
                .addCondition("message_id", messageId.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try { result.add(UUID.fromString(rs.getString("viewer_uuid"))); }
                catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadViewers: " + e.getMessage());
        }
        return result;
    }

    public Map<UUID, List<UUID>> loadViewersBatch(List<UUID> messageIds) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        if (messageIds.isEmpty()) return result;

        String placeholders = String.join(",", java.util.Collections.nCopies(messageIds.size(), "?"));
        String rawSql = "SELECT message_id, viewer_uuid FROM " + tablePrefix
                + "message_viewers WHERE message_id IN (" + placeholders + ")";
        Object[] params = messageIds.stream().map(UUID::toString).toArray();

        try (SQLQuery query = sql().createQuery()
                .withPreparedSQL(rawSql)
                .setParams(params)
                .execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try {
                    UUID msgId = UUID.fromString(rs.getString("message_id"));
                    UUID viewerId = UUID.fromString(rs.getString("viewer_uuid"));
                    result.computeIfAbsent(msgId, k -> new ArrayList<>()).add(viewerId);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadViewersBatch: " + e.getMessage());
        }
        return result;
    }

    // ============================================================
    //  loadRecentHistory / loadHistoryBySender
    // ============================================================

    public List<HistoryRow> loadRecentHistory(long cutoffMs, int limit) {
        List<HistoryRow> result = new ArrayList<>();
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "message_history")
                .selectColumns("id", "sender_uuid", "sender_name", "channel_id", "raw_message", "timestamp")
                .addCondition("timestamp", ">=", cutoffMs)
                .orderBy("timestamp", true)
                .setLimit(limit)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
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
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadRecentHistory: " + e.getMessage());
        }
        return result;
    }

    public List<HistoryRow> loadHistoryBySender(UUID senderUuid, int limit) {
        List<HistoryRow> result = new ArrayList<>();
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "message_history")
                .selectColumns("id", "sender_uuid", "sender_name", "channel_id", "raw_message", "timestamp")
                .addCondition("sender_uuid", senderUuid.toString())
                .orderBy("timestamp", false)
                .setLimit(limit)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
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
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadHistoryBySender: " + e.getMessage());
        }
        return result;
    }

    public record HistoryRow(UUID id, UUID senderUuid, String senderName,
                             String channelId, String rawMessage, long timestamp) {}

    // ============================================================
    //  Ники
    // ============================================================

    public void setNick(UUID uuid, String nick) {
        sql().createReplace(tablePrefix + "nicks")
                .setColumnNames("uuid", "nick")
                .setParams(uuid.toString(), nick)
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB setNick: " + exception.getMessage())
                );
    }

    public String getNick(UUID uuid) {
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "nicks")
                .selectColumns("nick")
                .addCondition("uuid", uuid.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            if (rs.next()) return rs.getString("nick");
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getNick: " + e.getMessage());
        }
        return null;
    }

    public void removeNick(UUID uuid) {
        sql().createDelete(tablePrefix + "nicks")
                .addCondition("uuid", uuid.toString())
                .build()
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB removeNick: " + exception.getMessage())
                );
    }

    public Map<UUID, String> getAllNicks() {
        Map<UUID, String> result = new HashMap<>();
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "nicks")
                .selectColumns("uuid", "nick")
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try {
                    result.put(UUID.fromString(rs.getString("uuid")), rs.getString("nick"));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getAllNicks: " + e.getMessage());
        }
        return result;
    }

    // ============================================================
    //  Соц-шпион
    // ============================================================

    public void setSocialSpy(UUID uuid, boolean enabled) {
        sql().createReplace(tablePrefix + "socialspy")
                .setColumnNames("uuid", "enabled")
                .setParams(uuid.toString(), enabled)
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB setSocialSpy: " + exception.getMessage())
                );
    }

    public boolean isSocialSpy(UUID uuid) {
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "socialspy")
                .selectColumns("enabled")
                .addCondition("uuid", uuid.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            if (rs.next()) return rs.getBoolean("enabled");
        } catch (SQLException e) {
            plugin.getLogger().warning("DB isSocialSpy: " + e.getMessage());
        }
        return false;
    }

    public Set<UUID> getAllSocialSpy() {
        Set<UUID> result = new HashSet<>();
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "socialspy")
                .selectColumns("uuid")
                .addCondition("enabled", true)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try { result.add(UUID.fromString(rs.getString("uuid"))); }
                catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getAllSocialSpy: " + e.getMessage());
        }
        return result;
    }

    // ============================================================
    //  Reply
    // ============================================================

    public void setReply(UUID viewer, UUID target) {
        sql().createReplace(tablePrefix + "replies")
                .setColumnNames("viewer", "target")
                .setParams(viewer.toString(), target.toString())
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB setReply: " + exception.getMessage())
                );
    }

    public UUID getReply(UUID viewer) {
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "replies")
                .selectColumns("target")
                .addCondition("viewer", viewer.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            if (rs.next()) return UUID.fromString(rs.getString("target"));
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getReply: " + e.getMessage());
        }
        return null;
    }

    public void clearReply(UUID viewer) {
        sql().createDelete(tablePrefix + "replies")
                .addCondition("viewer", viewer.toString())
                .build()
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB clearReply: " + exception.getMessage())
                );
    }

    // ============================================================
    //  Активный канал
    // ============================================================

    public void setActiveChannel(UUID uuid, String channelId) {
        sql().createReplace(tablePrefix + "channels")
                .setColumnNames("uuid", "channel_id")
                .setParams(uuid.toString(), channelId)
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB setActiveChannel: " + exception.getMessage())
                );
    }

    public String getActiveChannel(UUID uuid) {
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "channels")
                .selectColumns("channel_id")
                .addCondition("uuid", uuid.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            if (rs.next()) return rs.getString("channel_id");
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getActiveChannel: " + e.getMessage());
        }
        return null;
    }

    public void clearActiveChannel(UUID uuid) {
        sql().createDelete(tablePrefix + "channels")
                .addCondition("uuid", uuid.toString())
                .build()
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB clearActiveChannel: " + exception.getMessage())
                );
    }

    // ============================================================
    //  Аудит правил
    // ============================================================

    public void logRule(String ruleId, UUID playerUuid, String playerName,
                        String message, long timestamp) {
        sql().createInsert(tablePrefix + "rules_log")
                .setColumnNames("rule_id", "player_uuid", "player_name", "message", "timestamp")
                .setParams(ruleId, playerUuid.toString(), playerName, message, timestamp)
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB logRule: " + exception.getMessage())
                );
    }

    // ============================================================
    //  Статистика (incrementStat / incrementStats)
    // ============================================================

    /**
     * Инкремент одной колонки (для совместимости).
     */
    public void incrementStat(UUID uuid, String column) {
        incrementStats(uuid, column);
    }

    /**
     * Инкремент нескольких колонок в одном запросе (оптимизация).
     */
    public void incrementStats(UUID uuid, String... columns) {
        if (columns.length == 0) return;

        for (String c : columns) {
            switch (c) {
                case "messages_sent", "messages_blocked", "swear_caught", "ads_caught" -> {}
                default -> { return; }
            }
        }

        async(() -> {
            try {
                // 1. UPDATE
                StringBuilder setClause = new StringBuilder();
                for (int i = 0; i < columns.length; i++) {
                    if (i > 0) setClause.append(", ");
                    setClause.append(columns[i]).append(" = ").append(columns[i]).append(" + 1");
                }

                Integer updated = sql().executeSQL(
                        "UPDATE " + tablePrefix + "player_stats SET " + setClause + " WHERE uuid = ?",
                        new Object[]{ uuid.toString() }
                );

                if (updated != null && updated > 0) return;

                // 2. INSERT
                StringBuilder cols = new StringBuilder("uuid");
                StringBuilder vals = new StringBuilder("?");
                for (String c : columns) {
                    cols.append(", ").append(c);
                    vals.append(", 1");
                }
                Object[] params = new Object[]{ uuid.toString() };
                sql().executeSQL(
                        "INSERT INTO " + tablePrefix + "player_stats (" + cols + ") VALUES (" + vals + ")",
                        params
                );
            } catch (Exception e) {
                plugin.getLogger().warning("DB incrementStats: " + e.getMessage());
            }
        });
    }

    public PlayerStats getPlayerStats(UUID uuid) {
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "player_stats")
                .selectColumns("messages_sent", "messages_blocked", "swear_caught", "ads_caught")
                .addCondition("uuid", uuid.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            if (rs.next()) {
                return new PlayerStats(
                        rs.getLong("messages_sent"),
                        rs.getLong("messages_blocked"),
                        rs.getLong("swear_caught"),
                        rs.getLong("ads_caught")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getPlayerStats: " + e.getMessage());
        }
        return new PlayerStats(0, 0, 0, 0);
    }

    public record PlayerStats(long messagesSent, long messagesBlocked,
                              long swearCaught, long adsCaught) {}

    // ============================================================
    //  Thread replies
    // ============================================================

    public void saveThreadReply(UUID id, UUID parentId, UUID authorUuid,
                                String authorName, String text, long timestamp) {
        sql().createReplace(tablePrefix + "thread_replies")
                .setColumnNames("id", "parent_id", "author_uuid", "author_name", "text", "timestamp")
                .setParams(id.toString(), parentId.toString(), authorUuid.toString(), authorName, text, timestamp)
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB saveThreadReply: " + exception.getMessage())
                );
    }

    public List<ThreadReplyRow> getThreadReplies(UUID parentId) {
        List<ThreadReplyRow> result = new ArrayList<>();
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "thread_replies")
                .selectColumns("id", "author_uuid", "author_name", "text", "timestamp")
                .addCondition("parent_id", parentId.toString())
                .orderBy("timestamp", true)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
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
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getThreadReplies: " + e.getMessage());
        }
        return result;
    }

    public Map<UUID, List<ThreadReplyRow>> getThreadRepliesBatch(List<UUID> parentIds) {
        Map<UUID, List<ThreadReplyRow>> result = new HashMap<>();
        if (parentIds.isEmpty()) return result;

        String placeholders = String.join(",", java.util.Collections.nCopies(parentIds.size(), "?"));
        String rawSql = "SELECT parent_id, id, author_uuid, author_name, text, timestamp FROM "
                + tablePrefix + "thread_replies WHERE parent_id IN (" + placeholders + ") ORDER BY timestamp ASC";
        Object[] params = parentIds.stream().map(UUID::toString).toArray();

        try (SQLQuery query = sql().createQuery()
                .withPreparedSQL(rawSql)
                .setParams(params)
                .execute()) {
            ResultSet rs = query.getResultSet();
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
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getThreadRepliesBatch: " + e.getMessage());
        }
        return result;
    }

    public void deleteThreadReplies(UUID parentId) {
        sql().createDelete(tablePrefix + "thread_replies")
                .addCondition("parent_id", parentId.toString())
                .build()
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB deleteThreadReplies: " + exception.getMessage())
                );
    }

    public record ThreadReplyRow(UUID id, UUID authorUuid, String authorName,
                                 String text, long timestamp) {}

    // ============================================================
    //  Group chats
    // ============================================================

    public UUID createGroup(String name, UUID ownerUuid) {
        UUID id = UUID.randomUUID();
        try {
            sql().createInsert(tablePrefix + "chat_groups")
                    .setColumnNames("id", "name", "owner_uuid", "created_at")
                    .setParams(id.toString(), name, ownerUuid.toString(), System.currentTimeMillis())
                    .execute();
            return id;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB createGroup: " + e.getMessage());
            return null;
        }
    }

    public boolean addGroupMember(UUID groupId, UUID memberUuid) {
        try {
            sql().createInsert(tablePrefix + "chat_group_members")
                    .setColumnNames("group_id", "member_uuid")
                    .setParams(groupId.toString(), memberUuid.toString())
                    .execute();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB addGroupMember: " + e.getMessage());
            return false;
        }
    }

    public boolean removeGroupMember(UUID groupId, UUID memberUuid) {
        try {
            sql().createDelete(tablePrefix + "chat_group_members")
                    .addCondition("group_id", groupId.toString())
                    .addCondition("member_uuid", memberUuid.toString())
                    .build().execute();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB removeGroupMember: " + e.getMessage());
            return false;
        }
    }

    public List<UUID> getGroupMembers(UUID groupId) {
        List<UUID> result = new ArrayList<>();
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "chat_group_members")
                .selectColumns("member_uuid")
                .addCondition("group_id", groupId.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try { result.add(UUID.fromString(rs.getString("member_uuid"))); }
                catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getGroupMembers: " + e.getMessage());
        }
        return result;
    }

    public GroupRow getGroupByName(String name) {
        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "chat_groups")
                .selectColumns("id", "name", "owner_uuid", "created_at")
                .addCondition("name", name)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            if (rs.next()) {
                return new GroupRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getLong("created_at")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getGroupByName: " + e.getMessage());
        }
        return null;
    }

    public void deleteGroup(UUID groupId) {
        async(() -> {
            try {
                sql().createDelete(tablePrefix + "chat_group_history")
                        .addCondition("group_id", groupId.toString())
                        .build().execute();
                sql().createDelete(tablePrefix + "chat_group_members")
                        .addCondition("group_id", groupId.toString())
                        .build().execute();
                sql().createDelete(tablePrefix + "chat_groups")
                        .addCondition("id", groupId.toString())
                        .build().execute();
            } catch (SQLException e) {
                plugin.getLogger().warning("DB deleteGroup: " + e.getMessage());
            }
        });
    }

    public void saveGroupMessage(UUID id, UUID groupId, UUID senderUuid,
                                 String senderName, String message, long timestamp) {
        sql().createInsert(tablePrefix + "chat_group_history")
                .setColumnNames("id", "group_id", "sender_uuid", "sender_name", "message", "timestamp")
                .setParams(id.toString(), groupId.toString(), senderUuid.toString(), senderName, message, timestamp)
                .executeAsync(
                        success -> {},
                        (exception, action) -> plugin.getLogger().warning("DB saveGroupMessage: " + exception.getMessage())
                );
    }

    public record GroupRow(UUID id, String name, UUID ownerUuid, long createdAt) {}

    // ============================================================
    //  CLOSE
    // ============================================================

    public void close() {
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Storage: не все задачи завершились за 5 сек.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            asyncExecutor = null;
        }
    }
}