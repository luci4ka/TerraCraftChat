package ru.terracraft.chat;

import de.exlll.configlib.YamlConfigurations;
import org.bstats.bukkit.Metrics;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.terracraft.chat.command.*;
import ru.terracraft.chat.config.ChatConfig;
import ru.terracraft.chat.format.ChatFormatter;
import ru.terracraft.chat.format.HeadDisplayManager;
import ru.terracraft.chat.format.TranslationManager;
import ru.terracraft.chat.game.ChatGameManager;
import ru.terracraft.chat.game.GamesConfig;
import ru.terracraft.chat.integration.*;
import ru.terracraft.chat.listener.ChatListener;
import ru.terracraft.chat.listener.InvListener;
import ru.terracraft.chat.listener.PlayerDataListener;
import ru.terracraft.chat.manager.*;
import ru.terracraft.chat.model.Channel;
import ru.terracraft.chat.model.ChatMessage;
import ru.terracraft.chat.moderation.ChatLogger;
import ru.terracraft.chat.moderation.ChatModerator;
import ru.terracraft.chat.moderation.RuleManager;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.Tasks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class TerraCraftChatPlugin extends JavaPlugin {

    private static TerraCraftChatPlugin instance;

    private ChatConfig chatConfig;

    private TranslationManager translationManager;
    private SkinHeadCache skinHeadCache;
    private EmojiManager emojiManager;
    private GamesConfig gamesConfig;
    private IgnoreManager ignoreManager;
    private NickManager nickManager;
    private ThreadManager threadManager;
    private ReplyManager replyManager;
    private SocialSpyManager socialSpyManager;
    private ChatModerator chatModerator;
    private RuleManager ruleManager;
    private ChatRedrawManager chatRedrawManager;
    private LocalAnswerManager localAnswerManager;
    private ChatMessageRegistry chatMessageRegistry;
    private UpdateNotifier updateNotifier;
    private ChatGameManager chatGameManager;
    private ChatLogger chatLogger;
    private DiscordWebhook discordWebhook;
    private AutoBroadcast autoBroadcast;
    private InvTokenManager invTokenManager;
    private HeadDisplayManager headDisplayManager;
    private ChatFormatter chatFormatter;
    private ChannelManager channelManager;
    private GroupManager groupManager;
    private ChatPlaceholders chatPlaceholders;
    private StorageManager storageManager;

    // ============================================================
    //  LIFECYCLE
    // ============================================================

    @Override
    public void onEnable() {
        instance = this;

        int pluginId = 34792;
        new Metrics(this, pluginId);

        ensurePermissions();
        loadConfigs();

        this.storageManager = new StorageManager(this);
        this.storageManager.init();

        this.translationManager = new TranslationManager(this);
        this.translationManager.loadAll();

        this.skinHeadCache = new SkinHeadCache(this);

        this.emojiManager = new EmojiManager(this);
        this.emojiManager.load();

        this.gamesConfig = new GamesConfig(this);
        this.gamesConfig.load();

        this.ignoreManager = new IgnoreManager(this, storageManager);
        this.nickManager = new NickManager(this, storageManager);
        this.invTokenManager = new InvTokenManager(this);
        this.replyManager = new ReplyManager(this, storageManager);
        this.socialSpyManager = new SocialSpyManager(this, storageManager);

        this.chatModerator = new ChatModerator(chatConfig);
        this.ruleManager = new RuleManager(this);
        this.ruleManager.load();

        this.localAnswerManager = new LocalAnswerManager(this, chatConfig);

        this.chatMessageRegistry = new ChatMessageRegistry(
                chatConfig.delete().cacheSize(),
                chatConfig.delete().ttlSeconds()
        );

        loadHistoryFromDbAsync();

        scheduleMaintenance();

        this.chatFormatter = new ChatFormatter(this, chatConfig);
        this.headDisplayManager = new HeadDisplayManager(
                this,
                chatConfig.display().headDisplayDuration()
        );

        this.channelManager = new ChannelManager(this, storageManager);
        loadChannels();

        this.groupManager = new GroupManager(this, storageManager);

        this.threadManager = new ThreadManager(
                this, chatMessageRegistry, chatFormatter, chatConfig
        );

        this.chatGameManager = new ChatGameManager(this, chatConfig, gamesConfig);

        this.discordWebhook = new DiscordWebhook(this, chatConfig);
        this.discordWebhook.start();

        this.autoBroadcast = new AutoBroadcast(this, chatConfig);
        this.autoBroadcast.start();

        if (chatConfig.logging().enabled()) {
            this.chatLogger = new ChatLogger(this);
            this.chatLogger.open();
        }
        this.chatRedrawManager = new ChatRedrawManager(this, chatFormatter);

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        getServer().getPluginManager().registerEvents(new InvListener(this), this);
        getServer().getPluginManager().registerEvents(
                new ChatListener(this, ignoreManager, headDisplayManager, chatFormatter), this
        );

        // Commands
        new RpCommand(this).register();
        registerCommands();

        // Scheduled
        this.chatGameManager.start();

        // Update checker
        this.updateNotifier = new UpdateNotifier(this);
        this.updateNotifier.checkAsync();

        // PAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.chatPlaceholders = new ChatPlaceholders(this);
            this.chatPlaceholders.register();
            getLogger().info("PlaceholderAPI expansion: %terracraftchat_...%");
        }

        printStartupSummary();
    }

    @Override
    public void onDisable() {
        if (headDisplayManager != null) headDisplayManager.shutdown();
        if (chatLogger != null) chatLogger.close();
        if (chatGameManager != null) chatGameManager.stop();
        if (autoBroadcast != null) autoBroadcast.stop();
        if (discordWebhook != null) discordWebhook.stop();

        if (translationManager != null) {
            translationManager.shutdown();
        }

        // Сначала дожидаемся фоновых задач (они могут писать в БД),
        // и только потом закрываем хранилище — иначе потеряем последние записи.
        Tasks.shutdown();

        if (storageManager != null) {
            storageManager.close();
        }

        instance = null;

        getLogger().info("TerraCraftChat disabled.");
    }

    // ============================================================
    //  CHANNELS
    // ============================================================

    private void loadChannels() {
        List<Channel> list = new ArrayList<>();
        var section = chatConfig.channels();

        for (var entry : section.channels().entrySet()) {
            var e = entry.getValue();
            list.add(new Channel(
                    entry.getKey(),
                    e.prefix(),
                    e.range(),
                    e.permission(),
                    e.format(),
                    e.cooldown(),
                    e.publicChannel()
            ));
        }

        if (list.isEmpty()) {
            getLogger().warning("Список каналов пуст — добавляю дефолтный local.");
            list.add(new Channel("local", "", 100, "", "local", 0, true));
        }

        channelManager.load(list, section.defaultChannel());
    }

    // ============================================================
    //  STARTUP SUMMARY
    // ============================================================

    private void printStartupSummary() {
        getLogger().info("--------------------------------------------------");
        getLogger().info(" TerraCraftChat v" + getPluginMeta().getVersion() + " loaded");
        getLogger().info(" Server: " + getServer().getName() + " " + getServer().getVersion());
        getLogger().info("");
        getLogger().info(" Dependencies:");
        getLogger().info("   CommandAPI:     " + status(getServer().getPluginManager().getPlugin("CommandAPI")));
        getLogger().info("   PlaceholderAPI: " + status(getServer().getPluginManager().getPlugin("PlaceholderAPI")));
        getLogger().info("   SkinsRestorer:  " + status(getServer().getPluginManager().getPlugin("SkinsRestorer")));
        getLogger().info("   LuckPerms:      " + status(getServer().getPluginManager().getPlugin("LuckPerms")));
        getLogger().info("   MineSQL:        " + status(getServer().getPluginManager().getPlugin("MineSQL")));
        getLogger().info("");
        getLogger().info(" Storage:");
        getLogger().info("   Type:           MineSQL");
        getLogger().info("   Available:      " + status(storageManager.isAvailable()));
        getLogger().info("   Table prefix:   " + chatConfig.storage().tablePrefix());
        getLogger().info("");
        getLogger().info(" Features:");
        getLogger().info("   Channels:       " + channelManager.all().size());
        getLogger().info("   Rules:          " + ruleManager.size());
        getLogger().info("   Threads:        " + status(chatConfig.threads().enabled()));
        getLogger().info("   Player heads:   " + status(chatConfig.display().showPlayerHeads()));
        getLogger().info("   Head source:    "
                + (skinHeadCache.usesSkinsRestorer() ? "SkinsRestorer" : "UUID"));
        getLogger().info("   Head display:   " + status(chatConfig.display().headDisplayEnabled()));
        getLogger().info("   Emojis:         " + status(chatConfig.emoji().enabled()));
        getLogger().info("   PAPI expansion: " + status(chatPlaceholders != null));
        getLogger().info("   Discord:        " + status(chatConfig.discord().enabled()));
        getLogger().info("   AutoBroadcast:  " + status(autoBroadcast != null));
        getLogger().info("   Chat Games:     " + status(chatConfig.chatGames().enabled())
                + (chatGameManager != null && chatGameManager.isManuallyDisabled()
                ? " (manually disabled)" : ""));
        getLogger().info("   Logging:        " + status(chatConfig.logging().enabled()));
        getLogger().info("");
        getLogger().info(" Loaded:");
        getLogger().info("   Locales:        " + translationManager.getLocaleCount());
        getLogger().info("   Emojis:         " + emojiManager.size());
        getLogger().info("   Games:          " + gamesConfig.getGames().size());
        getLogger().info("--------------------------------------------------");
    }

    private String status(Plugin plugin) {
        if (plugin == null) return "not found";
        if (!plugin.isEnabled()) return "disabled";
        return "v" + plugin.getPluginMeta().getVersion();
    }

    private String status(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    // ============================================================
    //  PERMISSIONS
    // ============================================================

    /** Право и его значение по умолчанию. */
    private record PermissionSpec(String node, PermissionDefault defaultValue) {}

    private static final List<PermissionSpec> PERMISSIONS = List.of(
            new PermissionSpec("terracraftchat.color", PermissionDefault.FALSE),
            new PermissionSpec("terracraftchat.msg", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.reply", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.ignore", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.thread", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.nick", PermissionDefault.OP),

            new PermissionSpec("terracraftchat.emoji", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.item", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.inv", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.inv.admin", PermissionDefault.OP),

            new PermissionSpec("terracraftchat.broadcast", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.admin", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.socialspy", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.moderator", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.channel.staff", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.stats", PermissionDefault.OP),

            new PermissionSpec("terracraftchat.group.create", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.group.use", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.group.invite", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.group.leave", PermissionDefault.TRUE),
            new PermissionSpec("terracraftchat.group.delete", PermissionDefault.TRUE),

            new PermissionSpec("terracraftchat.bypass.ads", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.bypass.swear", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.bypass.levenshtein", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.bypass.flood", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.bypass.crash", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.bypass.cooldown", PermissionDefault.OP),
            new PermissionSpec("terracraftchat.bypass.duplicate", PermissionDefault.OP)
    );

    /**
     * Права объявлены в {@code paper-plugin.yml} (их видят LuckPerms и
     * {@code /permissions} сразу). Это подстраховка для серверов, где секция
     * {@code permissions} не читается: незарегистрированное право нельзя
     * выдать плагином прав, а {@code hasPermission} вернёт false и «сломает»,
     * например, {@code /msg} для обычных игроков.
     */
    private void ensurePermissions() {
        var manager = getServer().getPluginManager();

        for (PermissionSpec spec : PERMISSIONS) {
            if (manager.getPermission(spec.node()) == null) {
                manager.addPermission(new Permission(spec.node(), spec.defaultValue()));
            }
        }
    }

    // ============================================================
    //  CONFIG
    // ============================================================

    public void loadConfigs() {
        Path dataFolder = getDataFolder().toPath();
        Path configPath = dataFolder.resolve("config.yml");
        this.chatConfig = YamlConfigurations.update(configPath, ChatConfig.class);
    }

    public boolean reload() {
        ChatConfig oldConfig = this.chatConfig;
        try {
            loadConfigs();
        } catch (Exception ex) {
            getLogger().severe("Ошибка загрузки конфига: " + ex.getMessage());
            this.chatConfig = oldConfig;
            return false;
        }

        try {
            if (autoBroadcast != null) autoBroadcast.reload(chatConfig);
            if (discordWebhook != null) discordWebhook.reload(chatConfig);
            if (localAnswerManager != null) localAnswerManager.reload(chatConfig);
            if (ruleManager != null) ruleManager.reload();

            this.gamesConfig.load();
            // chatGameManager.reload сохраняет manuallyDisabled
            this.chatGameManager.reload(chatConfig);

            this.emojiManager.load();

            this.chatFormatter.reload(chatConfig);
            this.threadManager.reload(chatConfig, chatFormatter);
            this.headDisplayManager.reload(chatConfig.display().headDisplayDuration());
            this.chatMessageRegistry.reload(
                    chatConfig.delete().cacheSize(),
                    chatConfig.delete().ttlSeconds()
            );
            this.chatMessageRegistry.cleanup();

            this.chatModerator.reload(chatConfig);

            if (chatConfig.logging().enabled() && chatLogger == null) {
                this.chatLogger = new ChatLogger(this);
                this.chatLogger.open();
            } else if (!chatConfig.logging().enabled() && chatLogger != null) {
                this.chatLogger.close();
                this.chatLogger = null;
            }

            this.translationManager.reload();
            loadChannels();

            // Сброс кэшей
            this.skinHeadCache.invalidateAll();
            this.groupManager.invalidateAllCache();
            if (chatPlaceholders != null) {
                chatPlaceholders.invalidateAll();
            }
        } catch (Exception ex) {
            getLogger().severe("Ошибка применения конфига: " + ex.getMessage());
            ex.printStackTrace();
            this.chatConfig = oldConfig;
            return false;
        }

        getLogger().warning("MineSQL настройки (host/db/user) НЕ перезагружены. "
                + "Используйте /minesql reload или рестарт сервера.");

        getLogger().info("Configuration reloaded.");
        return true;
    }

    /**
     * Периодические задачи обслуживания (все — асинхронные, через
     * Paper AsyncScheduler).
     */
    private void scheduleMaintenance() {
        // Чистка кэша сообщений
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> chatMessageRegistry.cleanup(),
                60, 60, TimeUnit.SECONDS);

        // Чистка истории по TTL
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (!storageManager.isAvailable()) return;
            long cutoff = System.currentTimeMillis()
                    - (chatConfig.delete().ttlSeconds() * 1000L);
            storageManager.cleanupHistory(cutoff);
        }, 5, 5, TimeUnit.MINUTES);

        // Чистка просроченных игноров
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (!storageManager.isAvailable()) return;
            storageManager.cleanupExpiredIgnores(System.currentTimeMillis());
        }, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Восстановление истории чата при старте — на виртуальном потоке,
     * чтобы не тормозить запуск сервера.
     */
    private void loadHistoryFromDbAsync() {
        if (!storageManager.isAvailable()) return;

        long cutoff = System.currentTimeMillis()
                - (chatConfig.delete().ttlSeconds() * 1000L);
        int limit = chatConfig.delete().cacheSize();

        Tasks.runAsync(this, "history-restore", () -> {
            long start = System.currentTimeMillis();

            var rows = storageManager.loadRecentHistory(cutoff, limit);
            if (rows.isEmpty()) return;

            List<UUID> ids = rows.stream().map(StorageManager.HistoryRow::id).toList();
            var viewersByMessage = storageManager.loadViewersBatch(ids);
            var repliesByMessage = storageManager.getThreadRepliesBatch(ids);

            for (var row : rows) {
                ChatMessage message = new ChatMessage(
                        row.id(), row.senderUuid(), row.senderName(),
                        row.rawMessage(), row.channelId()
                );

                for (UUID viewerId : viewersByMessage.getOrDefault(row.id(), List.of())) {
                    message.addViewer(viewerId);
                }

                for (var reply : repliesByMessage.getOrDefault(row.id(), List.of())) {
                    message.addReply(new ChatMessage.ThreadReply(
                            reply.id(), reply.authorUuid(), reply.authorName(),
                            reply.text(), reply.timestamp()
                    ));
                }

                chatMessageRegistry.registerWithoutThreadLoad(message);
            }

            getLogger().info("Загружено сообщений из истории: " + rows.size()
                    + " за " + (System.currentTimeMillis() - start) + " мс");
        });
    }

    // ============================================================
    //  COMMANDS
    // ============================================================

    private void registerCommands() {
        new MsgCommand(this).register();
        new ReplyCommand(this).register();
        new IgnoreCommand(this).register();
        new ChatReloadCommand(this).register();
        new SocialSpyCommand(this).register();
        new DeleteCommand(this).register();
        new BroadcastCommand(this).register();
        new InvCommand(this).register();
        new ThreadReplyCommand(this).register();
        new NickCommand(this).register();
        new ChannelCommand(this).register();
        new ChatHistoryCommand(this).register();
        new ChatStatsCommand(this).register();
        new ChatPurgeCommand(this).register();
        new ChatGroupCommand(this).register();
        new ChatGamesCommand(this).register();
    }

    // ============================================================
    //  GETTERS
    // ============================================================

    public static TerraCraftChatPlugin getInstance() { return instance; }

    public ChatConfig getChatConfig() { return chatConfig; }
    public ChatFormatter getChatFormatter() { return chatFormatter; }
    public ChatLogger getChatLogger() { return chatLogger; }
    public ChatMessageRegistry getChatMessageRegistry() { return chatMessageRegistry; }
    public ChatModerator getChatModerator() { return chatModerator; }
    public RuleManager getRuleManager() { return ruleManager; }
    public ChatGameManager getChatGameManager() { return chatGameManager; }
    public AutoBroadcast getAutoBroadcast() { return autoBroadcast; }
    public DiscordWebhook getDiscordWebhook() { return discordWebhook; }
    public EmojiManager getEmojiManager() { return emojiManager; }
    public HeadDisplayManager getHeadDisplayManager() { return headDisplayManager; }
    public IgnoreManager getIgnoreManager() { return ignoreManager; }
    public LocalAnswerManager getLocalAnswerManager() { return localAnswerManager; }
    public ReplyManager getReplyManager() { return replyManager; }
    public SkinHeadCache getSkinHeadCache() { return skinHeadCache; }
    public SocialSpyManager getSocialSpyManager() { return socialSpyManager; }
    public TranslationManager getTranslationManager() { return translationManager; }
    public ThreadManager getThreadManager() { return threadManager; }
    public GamesConfig getGamesConfig() { return gamesConfig; }
    public UpdateNotifier getUpdateNotifier() { return updateNotifier; }
    public NickManager getNickManager() { return nickManager; }
    public ChannelManager getChannelManager() { return channelManager; }
    public GroupManager getGroupManager() { return groupManager; }
    public InvTokenManager getInvTokenManager() { return invTokenManager; }
    public ChatRedrawManager getChatRedrawManager() { return chatRedrawManager; }
    public ChatPlaceholders getChatPlaceholders() { return chatPlaceholders; }
    public StorageManager getStorageManager() { return storageManager; }
}
