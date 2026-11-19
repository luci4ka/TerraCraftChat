package ru.terracraft.chat.storage;

import cc.carm.lib.easysql.api.SQLManager;
import cc.carm.lib.easysql.api.SQLQuery;
import cc.carm.plugin.minesql.MineSQL;
import org.bukkit.Bukkit;
import ru.terracraft.chat.TerraCraftChatPlugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Хранилище (MineSQL / MariaDB).
 *
 * <p>Все <b>записи</b> идут через один упорядоченный writer-поток: раньше часть
 * запросов уходила в пул EasySQL ({@code executeAsync}), часть — в собственный
 * executor, из-за чего порядок не гарантировался (например, «добавить в игнор» →
 * «убрать из игнора» могло примениться наоборот).
 *
 * <p>Все <b>чтения</b> синхронные и должны вызываться только с
 * {@link ru.terracraft.chat.util.Tasks} (виртуальные потоки), а не из main.
 *
 * <p>Если MineSQL нет или источник не настроен — хранилище помечается
 * недоступным, и все методы превращаются в no-op (плагин продолжает работать).
 */
public class StorageManager {

    public static final String SOURCE_NAME = "terracraft-chat";

    /** Лимит плейсхолдеров в одном IN(...). */
    private static final int IN_CHUNK_SIZE = 500;

    /** Сколько пар (message_id, viewer_uuid) писать одним INSERT. */
    private static final int VIEWER_CHUNK_SIZE = 100;

    private static final Set<String> STAT_COLUMNS = Set.of(
            "messages_sent", "messages_blocked", "swear_caught", "ads_caught"
    );

    private final TerraCraftChatPlugin plugin;

    private SQLManager sqlManager;
    private String tablePrefix;
    private volatile boolean available;
    private ExecutorService writer;

    public StorageManager(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    /** Доступно ли хранилище (MineSQL найден и источник настроен). */
    public boolean isAvailable() {
        return available;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    // ============================================================
    //  INIT / MIGRATE
    // ============================================================

    public void init() {
        var storage = plugin.getChatConfig().storage();
        this.tablePrefix = storage.tablePrefix();

        if (this.tablePrefix == null || !this.tablePrefix.matches("[a-zA-Z0-9_]*")) {
            plugin.getLogger().warning("Storage: некорректный tablePrefix '" + tablePrefix
                    + "', сбрасываю в 'tc_'");
            this.tablePrefix = "tc_";
        }

        if (Bukkit.getPluginManager().getPlugin("MineSQL") == null) {
            plugin.getLogger().warning("MineSQL не найден — хранилище отключено "
                    + "(история, игноры, статистика работать не будут).");
            return;
        }

        try {
            this.sqlManager = MineSQL.getRegistry().get(SOURCE_NAME);
            if (sqlManager == null) {
                plugin.getLogger().severe("MineSQL источник '" + SOURCE_NAME + "' не найден! "
                        + "Добавьте его в plugins/MineSQL/config.yml");
                return;
            }
            migrate();
            this.available = true;
            plugin.getLogger().info("Storage connected: MineSQL (" + SOURCE_NAME + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("Storage init failed: " + e.getMessage());
            return;
        }

        // Один поток на все записи: FIFO-порядок + никаких гонок
        this.writer = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon().name("TerraCraftChat-Storage").factory());
    }

    /**
     * Миграции для MariaDB (InnoDB, utf8mb4).
     * Все таблицы создаются с ENGINE=InnoDB и DEFAULT CHARSET=utf8mb4,
     * чтобы корректно хранить эмодзи и любые Unicode-ники.
     */
    private void migrate() {
        String p = tablePrefix;
        String suffix = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "ignores (" +
                "viewer VARCHAR(36) NOT NULL, " +
                "target VARCHAR(36) NOT NULL, " +
                "expires_at BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (viewer, target))" + suffix, "ignores");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "message_history (" +
                "id VARCHAR(36) NOT NULL, " +
                "sender_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(64) NOT NULL, " +
                "channel_id VARCHAR(64) NOT NULL, " +
                "raw_message MEDIUMTEXT NOT NULL, " +
                "timestamp BIGINT NOT NULL, " +
                "PRIMARY KEY (id))" + suffix, "message_history");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "nicks (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "nick VARCHAR(64) NOT NULL, " +
                "PRIMARY KEY (uuid))" + suffix, "nicks");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "socialspy (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "enabled TINYINT(1) NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid))" + suffix, "socialspy");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "replies (" +
                "viewer VARCHAR(36) NOT NULL, " +
                "target VARCHAR(36) NOT NULL, " +
                "PRIMARY KEY (viewer))" + suffix, "replies");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "channels (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "channel_id VARCHAR(64) NOT NULL, " +
                "PRIMARY KEY (uuid))" + suffix, "channels");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "rules_log (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, " +
                "rule_id VARCHAR(64) NOT NULL, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(64) NOT NULL, " +
                "message MEDIUMTEXT NULL, " +
                "timestamp BIGINT NOT NULL, " +
                "PRIMARY KEY (id))" + suffix, "rules_log");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "player_stats (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "messages_sent BIGINT NOT NULL DEFAULT 0, " +
                "messages_blocked BIGINT NOT NULL DEFAULT 0, " +
                "swear_caught BIGINT NOT NULL DEFAULT 0, " +
                "ads_caught BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid))" + suffix, "player_stats");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "thread_replies (" +
                "id VARCHAR(36) NOT NULL, " +
                "parent_id VARCHAR(36) NOT NULL, " +
                "author_uuid VARCHAR(36) NOT NULL, " +
                "author_name VARCHAR(64) NOT NULL, " +
                "text MEDIUMTEXT NOT NULL, " +
                "timestamp BIGINT NOT NULL, " +
                "PRIMARY KEY (id))" + suffix, "thread_replies");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "message_viewers (" +
                "message_id VARCHAR(36) NOT NULL, " +
                "viewer_uuid VARCHAR(36) NOT NULL, " +
                "PRIMARY KEY (message_id, viewer_uuid))" + suffix, "message_viewers");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "chat_groups (" +
                "id VARCHAR(36) NOT NULL, " +
                "name VARCHAR(64) NOT NULL, " +
                "owner_uuid VARCHAR(36) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "PRIMARY KEY (id), " +
                "UNIQUE KEY uk_" + p + "group_name (name))" + suffix, "chat_groups");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "chat_group_members (" +
                "group_id VARCHAR(36) NOT NULL, " +
                "member_uuid VARCHAR(36) NOT NULL, " +
                "PRIMARY KEY (group_id, member_uuid))" + suffix, "chat_group_members");

        executeRaw("CREATE TABLE IF NOT EXISTS " + p + "chat_group_history (" +
                "id VARCHAR(36) NOT NULL, " +
                "group_id VARCHAR(36) NOT NULL, " +
                "sender_uuid VARCHAR(36) NOT NULL, " +
                "sender_name VARCHAR(64) NOT NULL, " +
                "message MEDIUMTEXT NOT NULL, " +
                "timestamp BIGINT NOT NULL, " +
                "PRIMARY KEY (id))" + suffix, "chat_group_history");

        // === Индексы (отдельно, безопасно) ===
        createIndex("idx_" + p + "history_ts", p + "message_history", "timestamp");
        createIndex("idx_" + p + "history_channel", p + "message_history", "channel_id, timestamp");
        createIndex("idx_" + p + "rules_ts", p + "rules_log", "timestamp");
        createIndex("idx_" + p + "rules_player", p + "rules_log", "player_uuid");
        createIndex("idx_" + p + "thread_parent", p + "thread_replies", "parent_id, timestamp");
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

    /**
     * CREATE INDEX в MariaDB не поддерживает IF NOT EXISTS до 10.5+.
     * Поэтому глушим только «Duplicate key name» (1061), остальное логируем.
     */
    private void createIndex(String indexName, String table, String columns) {
        try {
            sql().executeSQL("CREATE INDEX " + indexName + " ON " + table + "(" + columns + ")");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (!msg.contains("duplicate") && !msg.contains("1061")) {
                plugin.getLogger().warning("Index " + indexName + " not created: " + e.getMessage());
            }
        }
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

    /**
     * Ставит запись в очередь упорядоченного writer-потока.
     * Все методы записи проходят только через него.
     */
    private void write(String what, SqlTask task) {
        if (!available || writer == null) return;

        try {
            writer.execute(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    plugin.getLogger().warning("DB " + what + ": " + e.getMessage());
                }
            });
        } catch (Throwable e) {
            // writer уже закрыт (выключение плагина)
            plugin.getLogger().fine("DB " + what + " skipped: " + e.getMessage());
        }
    }

    /** Задача записи: может бросать SQLException (выполняется на writer-потоке). */
    @FunctionalInterface
    private interface SqlTask {
        void run() throws Exception;
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static <T> List<List<T>> chunk(List<T> source, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            chunks.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return chunks;
    }

    // ============================================================
    //  Ignore
    // ============================================================

    public void addIgnore(UUID viewer, UUID target, long expiresAtMs) {
        write("addIgnore", () -> {
            sql().createReplace(tablePrefix + "ignores")
                    .setColumnNames("viewer", "target", "expires_at")
                    .setParams(viewer.toString(), target.toString(), expiresAtMs)
                    .execute();
        });
    }

    public void addIgnore(UUID viewer, UUID target) {
        addIgnore(viewer, target, 0L);
    }

    public void removeIgnore(UUID viewer, UUID target) {
        write("removeIgnore", () -> {
            sql().createDelete(tablePrefix + "ignores")
                    .addCondition("viewer", viewer.toString())
                    .addCondition("target", target.toString())
                    .build()
                    .execute();
        });
    }

    public Set<UUID> getIgnores(UUID viewer) {
        Set<UUID> result = new HashSet<>();
        if (!available) return result;

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
                } catch (IllegalArgumentException ignored) {
                    // битая строка в БД
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
        write("cleanupExpiredIgnores", () -> {
            sql().createDelete(tablePrefix + "ignores")
                    .addCondition("expires_at", ">", 0)
                    .addCondition("expires_at", "<", nowMs)
                    .build()
                    .execute();
        });
    }

    // ============================================================
    //  History
    // ============================================================

    public void saveMessage(UUID id, UUID senderUuid, String senderName,
                            String channelId, String rawMessage, long timestamp) {
        write("saveMessage", () -> {
            sql().createReplace(tablePrefix + "message_history")
                    .setColumnNames("id", "sender_uuid", "sender_name", "channel_id", "raw_message", "timestamp")
                    .setParams(id.toString(), senderUuid.toString(), senderName, channelId, rawMessage, timestamp)
                    .execute();
        });
    }

    public void deleteMessage(UUID id) {
        write("deleteMessage", () -> {
            sql().createDelete(tablePrefix + "message_viewers")
                    .addCondition("message_id", id.toString())
                    .build().execute();
            sql().createDelete(tablePrefix + "thread_replies")
                    .addCondition("parent_id", id.toString())
                    .build().execute();
            sql().createDelete(tablePrefix + "message_history")
                    .addCondition("id", id.toString())
                    .build().execute();
        });
    }

    /**
     * Удаляет старые сообщения и связанные с ними просмотры/ответы.
     *
     * <p>ИСПРАВЛЕНИЕ: {@code addCondition(column, "IN", "(subquery)")} в EasySQL
     * оборачивает значение в кавычки → MariaDB получает {@code IN '(SELECT ...)'}
     * и падает с 1064. Поэтому подзапрос встраивается в текст SQL,
     * а граница — через prepared-параметр.
     */
    public void cleanupHistory(long olderThanMs) {
        write("cleanupHistory", () -> {
            String selectOldIds = "SELECT id FROM " + tablePrefix
                    + "message_history WHERE timestamp < ?";

            sql().executeSQL(
                    "DELETE FROM " + tablePrefix + "message_viewers WHERE message_id IN ("
                            + selectOldIds + ")",
                    new Object[]{ olderThanMs }
            );

            sql().executeSQL(
                    "DELETE FROM " + tablePrefix + "thread_replies WHERE parent_id IN ("
                            + selectOldIds + ")",
                    new Object[]{ olderThanMs }
            );

            sql().createDelete(tablePrefix + "message_history")
                    .addCondition("timestamp", "<", olderThanMs)
                    .build().execute();
        });
    }

    public void purgeHistory(long olderThanMs) {
        cleanupHistory(olderThanMs);
    }

    // ============================================================
    //  Message viewers — BATCH
    // ============================================================

    /**
     * Пишет аудиторию сообщения пачками (INSERT IGNORE, чтобы повтор
     * пары (message_id, viewer_uuid) не ронял весь батч).
     *
     * <p>Раньше одним INSERT отправлялись все зрители — на сервере
     * с сотней онлайна это гигантский запрос на каждое сообщение.
     */
    public void saveMessageViewers(UUID messageId, Set<UUID> viewers) {
        if (viewers.isEmpty()) return;

        List<UUID> snapshot = List.copyOf(viewers);

        write("saveMessageViewers", () -> {
            String id = messageId.toString();

            for (List<UUID> chunk : chunk(snapshot, VIEWER_CHUNK_SIZE)) {
                StringBuilder sql = new StringBuilder(
                        "INSERT IGNORE INTO " + tablePrefix
                                + "message_viewers (message_id, viewer_uuid) VALUES ");

                Object[] params = new Object[chunk.size() * 2];
                int i = 0;
                for (int index = 0; index < chunk.size(); index++) {
                    if (index > 0) sql.append(",");
                    sql.append("(?, ?)");
                    params[i++] = id;
                    params[i++] = chunk.get(index).toString();
                }

                sql().executeSQL(sql.toString(), params);
            }
        });
    }

    public List<UUID> loadViewers(UUID messageId) {
        List<UUID> result = new ArrayList<>();
        if (!available) return result;

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "message_viewers")
                .selectColumns("viewer_uuid")
                .addCondition("message_id", messageId.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try {
                    result.add(UUID.fromString(rs.getString("viewer_uuid")));
                } catch (IllegalArgumentException ignored) {
                    // битая строка
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadViewers: " + e.getMessage());
        }
        return result;
    }

    public Map<UUID, List<UUID>> loadViewersBatch(List<UUID> messageIds) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        if (!available || messageIds.isEmpty()) return result;

        for (List<UUID> chunk : chunk(messageIds, IN_CHUNK_SIZE)) {
            String rawSql = "SELECT message_id, viewer_uuid FROM " + tablePrefix
                    + "message_viewers WHERE message_id IN (" + placeholders(chunk.size()) + ")";
            Object[] params = chunk.stream().map(UUID::toString).toArray();

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
                    } catch (IllegalArgumentException ignored) {
                        // битая строка
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("DB loadViewersBatch: " + e.getMessage());
            }
        }
        return result;
    }

    // ============================================================
    //  loadRecentHistory / loadHistoryBySender
    // ============================================================

    public List<HistoryRow> loadRecentHistory(long cutoffMs, int limit) {
        List<HistoryRow> result = new ArrayList<>();
        if (!available) return result;

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "message_history")
                .selectColumns("id", "sender_uuid", "sender_name", "channel_id", "raw_message", "timestamp")
                .addCondition("timestamp", ">=", cutoffMs)
                .orderBy("timestamp", true)
                .setLimit(limit)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                HistoryRow row = readHistoryRow(rs);
                if (row != null) result.add(row);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadRecentHistory: " + e.getMessage());
        }
        return result;
    }

    public List<HistoryRow> loadHistoryBySender(UUID senderUuid, int limit) {
        List<HistoryRow> result = new ArrayList<>();
        if (!available) return result;

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "message_history")
                .selectColumns("id", "sender_uuid", "sender_name", "channel_id", "raw_message", "timestamp")
                .addCondition("sender_uuid", senderUuid.toString())
                .orderBy("timestamp", false)
                .setLimit(limit)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                HistoryRow row = readHistoryRow(rs);
                if (row != null) result.add(row);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB loadHistoryBySender: " + e.getMessage());
        }
        return result;
    }

    private HistoryRow readHistoryRow(ResultSet rs) throws SQLException {
        try {
            return new HistoryRow(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("sender_uuid")),
                    rs.getString("sender_name"),
                    rs.getString("channel_id"),
                    rs.getString("raw_message"),
                    rs.getLong("timestamp")
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record HistoryRow(UUID id, UUID senderUuid, String senderName,
                             String channelId, String rawMessage, long timestamp) {}

    // ============================================================
    //  Ники
    // ============================================================

    public void setNick(UUID uuid, String nick) {
        write("setNick", () -> sql().createReplace(tablePrefix + "nicks")
                .setColumnNames("uuid", "nick")
                .setParams(uuid.toString(), nick)
                .execute());
    }

    public String getNick(UUID uuid) {
        if (!available) return null;

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
        write("removeNick", () -> sql().createDelete(tablePrefix + "nicks")
                .addCondition("uuid", uuid.toString())
                .build()
                .execute());
    }

    public Map<UUID, String> getAllNicks() {
        Map<UUID, String> result = new HashMap<>();
        if (!available) return result;

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "nicks")
                .selectColumns("uuid", "nick")
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try {
                    result.put(UUID.fromString(rs.getString("uuid")), rs.getString("nick"));
                } catch (IllegalArgumentException ignored) {
                    // битая строка
                }
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
        write("setSocialSpy", () -> sql().createReplace(tablePrefix + "socialspy")
                .setColumnNames("uuid", "enabled")
                .setParams(uuid.toString(), enabled)
                .execute());
    }

    public boolean isSocialSpy(UUID uuid) {
        if (!available) return false;

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
        if (!available) return result;

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "socialspy")
                .selectColumns("uuid")
                .addCondition("enabled", true)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try {
                    result.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                    // битая строка
                }
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
        write("setReply", () -> sql().createReplace(tablePrefix + "replies")
                .setColumnNames("viewer", "target")
                .setParams(viewer.toString(), target.toString())
                .execute());
    }

    public UUID getReply(UUID viewer) {
        if (!available) return null;

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "replies")
                .selectColumns("target")
                .addCondition("viewer", viewer.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            if (rs.next()) return UUID.fromString(rs.getString("target"));
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().warning("DB getReply: " + e.getMessage());
        }
        return null;
    }

    public void clearReply(UUID viewer) {
        write("clearReply", () -> sql().createDelete(tablePrefix + "replies")
                .addCondition("viewer", viewer.toString())
                .build()
                .execute());
    }

    // ============================================================
    //  Активный канал
    // ============================================================

    public void setActiveChannel(UUID uuid, String channelId) {
        write("setActiveChannel", () -> sql().createReplace(tablePrefix + "channels")
                .setColumnNames("uuid", "channel_id")
                .setParams(uuid.toString(), channelId)
                .execute());
    }

    public String getActiveChannel(UUID uuid) {
        if (!available) return null;

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
        write("clearActiveChannel", () -> sql().createDelete(tablePrefix + "channels")
                .addCondition("uuid", uuid.toString())
                .build()
                .execute());
    }

    // ============================================================
    //  Аудит правил
    // ============================================================

    public void logRule(String ruleId, UUID playerUuid, String playerName,
                        String message, long timestamp) {
        write("logRule", () -> sql().createInsert(tablePrefix + "rules_log")
                .setColumnNames("rule_id", "player_uuid", "player_name", "message", "timestamp")
                .setParams(ruleId, playerUuid.toString(), playerName, message, timestamp)
                .execute());
    }

    // ============================================================
    //  Статистика (атомарный upsert MariaDB)
    // ============================================================

    public void incrementStat(UUID uuid, String column) {
        incrementStats(uuid, column);
    }

    /**
     * Атомарный INSERT ... ON DUPLICATE KEY UPDATE — вместо UPDATE-then-INSERT
     * (гонка, потеря инкремента).
     */
    public void incrementStats(UUID uuid, String... columns) {
        if (columns.length == 0) return;

        // Защита от подстановки произвольных имён столбцов
        for (String column : columns) {
            if (!STAT_COLUMNS.contains(column)) return;
        }

        write("incrementStats", () -> {
            StringBuilder cols = new StringBuilder("uuid");
            StringBuilder vals = new StringBuilder("?");
            StringBuilder update = new StringBuilder();

            for (int i = 0; i < columns.length; i++) {
                cols.append(", ").append(columns[i]);
                vals.append(", 1");
                if (i > 0) update.append(", ");
                update.append(columns[i]).append(" = ").append(columns[i]).append(" + 1");
            }

            sql().executeSQL(
                    "INSERT INTO " + tablePrefix + "player_stats (" + cols + ") VALUES (" + vals + ") "
                            + "ON DUPLICATE KEY UPDATE " + update,
                    new Object[]{ uuid.toString() }
            );
        });
    }

    public PlayerStats getPlayerStats(UUID uuid) {
        if (!available) return new PlayerStats(0, 0, 0, 0);

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
        write("saveThreadReply", () -> sql().createReplace(tablePrefix + "thread_replies")
                .setColumnNames("id", "parent_id", "author_uuid", "author_name", "text", "timestamp")
                .setParams(id.toString(), parentId.toString(), authorUuid.toString(),
                        authorName, text, timestamp)
                .execute());
    }

    public List<ThreadReplyRow> getThreadReplies(UUID parentId) {
        List<ThreadReplyRow> result = new ArrayList<>();
        if (!available) return result;

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "thread_replies")
                .selectColumns("id", "author_uuid", "author_name", "text", "timestamp")
                .addCondition("parent_id", parentId.toString())
                .orderBy("timestamp", true)
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                ThreadReplyRow row = readThreadReply(rs);
                if (row != null) result.add(row);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getThreadReplies: " + e.getMessage());
        }
        return result;
    }

    public Map<UUID, List<ThreadReplyRow>> getThreadRepliesBatch(List<UUID> parentIds) {
        Map<UUID, List<ThreadReplyRow>> result = new HashMap<>();
        if (!available || parentIds.isEmpty()) return result;

        for (List<UUID> chunk : chunk(parentIds, IN_CHUNK_SIZE)) {
            String rawSql = "SELECT parent_id, id, author_uuid, author_name, text, timestamp FROM "
                    + tablePrefix + "thread_replies WHERE parent_id IN ("
                    + placeholders(chunk.size()) + ") ORDER BY timestamp ASC";
            Object[] params = chunk.stream().map(UUID::toString).toArray();

            try (SQLQuery query = sql().createQuery()
                    .withPreparedSQL(rawSql)
                    .setParams(params)
                    .execute()) {
                ResultSet rs = query.getResultSet();
                while (rs.next()) {
                    ThreadReplyRow row = readThreadReply(rs);
                    if (row == null) continue;
                    UUID parentId = UUID.fromString(rs.getString("parent_id"));
                    result.computeIfAbsent(parentId, k -> new ArrayList<>()).add(row);
                }
            } catch (SQLException | IllegalArgumentException e) {
                plugin.getLogger().warning("DB getThreadRepliesBatch: " + e.getMessage());
            }
        }
        return result;
    }

    private ThreadReplyRow readThreadReply(ResultSet rs) throws SQLException {
        try {
            return new ThreadReplyRow(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("author_uuid")),
                    rs.getString("author_name"),
                    rs.getString("text"),
                    rs.getLong("timestamp")
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void deleteThreadReplies(UUID parentId) {
        write("deleteThreadReplies", () -> sql().createDelete(tablePrefix + "thread_replies")
                .addCondition("parent_id", parentId.toString())
                .build()
                .execute());
    }

    public record ThreadReplyRow(UUID id, UUID authorUuid, String authorName,
                                 String text, long timestamp) {}

    // ============================================================
    //  Group chats
    // ============================================================

    /** Создаёт группу (I/O — вызывать через {@link ru.terracraft.chat.util.Tasks}). */
    public UUID createGroup(String name, UUID ownerUuid) {
        if (!available) return null;

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

    /** @return true если участник добавлен (или уже был) */
    public boolean addGroupMember(UUID groupId, UUID memberUuid) {
        if (!available) return false;

        try {
            sql().createInsert(tablePrefix + "chat_group_members")
                    .setColumnNames("group_id", "member_uuid")
                    .setParams(groupId.toString(), memberUuid.toString())
                    .execute();
            return true;
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            // 1062 = Duplicate entry (участник уже в группе) — не ошибка
            if (msg.contains("duplicate") || msg.contains("1062")) {
                return true;
            }
            plugin.getLogger().warning("DB addGroupMember: " + e.getMessage());
            return false;
        }
    }

    public boolean removeGroupMember(UUID groupId, UUID memberUuid) {
        if (!available) return false;

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
        if (!available) return result;

        try (SQLQuery query = sql().createQuery()
                .inTable(tablePrefix + "chat_group_members")
                .selectColumns("member_uuid")
                .addCondition("group_id", groupId.toString())
                .build().execute()) {
            ResultSet rs = query.getResultSet();
            while (rs.next()) {
                try {
                    result.add(UUID.fromString(rs.getString("member_uuid")));
                } catch (IllegalArgumentException ignored) {
                    // битая строка
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB getGroupMembers: " + e.getMessage());
        }
        return result;
    }

    public GroupRow getGroupByName(String name) {
        if (!available) return null;

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
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().warning("DB getGroupByName: " + e.getMessage());
        }
        return null;
    }

    public void deleteGroup(UUID groupId) {
        write("deleteGroup", () -> {
            sql().createDelete(tablePrefix + "chat_group_history")
                    .addCondition("group_id", groupId.toString())
                    .build().execute();
            sql().createDelete(tablePrefix + "chat_group_members")
                    .addCondition("group_id", groupId.toString())
                    .build().execute();
            sql().createDelete(tablePrefix + "chat_groups")
                    .addCondition("id", groupId.toString())
                    .build().execute();
        });
    }

    public void saveGroupMessage(UUID id, UUID groupId, UUID senderUuid,
                                 String senderName, String message, long timestamp) {
        write("saveGroupMessage", () -> sql().createInsert(tablePrefix + "chat_group_history")
                .setColumnNames("id", "group_id", "sender_uuid", "sender_name", "message", "timestamp")
                .setParams(id.toString(), groupId.toString(), senderUuid.toString(),
                        senderName, message, timestamp)
                .execute());
    }

    public record GroupRow(UUID id, String name, UUID ownerUuid, long createdAt) {}

    // ============================================================
    //  CLOSE
    // ============================================================

    /** Дожидается, пока writer допишет всё, что накопилось в очереди. */
    public boolean flush(long timeoutSeconds) {
        if (writer == null) return true;

        CompletableFuture<Void> barrier = new CompletableFuture<>();
        try {
            writer.execute(() -> barrier.complete(null));
        } catch (Throwable e) {
            return false;
        }
        try {
            barrier.get(timeoutSeconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void close() {
        if (writer != null) {
            if (!flush(5)) {
                plugin.getLogger().warning("Storage: очередь записей не успела опустеть за 5 сек.");
            }
            writer.shutdown();
            try {
                if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                    writer.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writer.shutdownNow();
            }
            writer = null;
        }
    }
}
