package ru.net.terracraft.chat;

import de.exlll.configlib.YamlConfigurations;
import org.bstats.bukkit.Metrics;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.command.*;
import ru.net.terracraft.chat.config.ChatConfig;
import ru.net.terracraft.chat.format.ChatFormatter;
import ru.net.terracraft.chat.format.HeadDisplayManager;
import ru.net.terracraft.chat.format.TranslationManager;
import ru.net.terracraft.chat.game.ChatGameManager;
import ru.net.terracraft.chat.game.GamesConfig;
import ru.net.terracraft.chat.integration.*;
import ru.net.terracraft.chat.listener.ChatListener;
import ru.net.terracraft.chat.listener.InvListener;
import ru.net.terracraft.chat.listener.PlayerDataListener;
import ru.net.terracraft.chat.manager.*;
import ru.net.terracraft.chat.model.Channel;
import ru.net.terracraft.chat.model.ChatMessage;
import ru.net.terracraft.chat.moderation.ChatLogger;
import ru.net.terracraft.chat.moderation.ChatModerator;
import ru.net.terracraft.chat.moderation.RuleManager;
import ru.net.terracraft.chat.storage.StorageManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        registerPermissions();
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
                this,
                chatConfig.delete().cacheSize(),
                chatConfig.delete().ttlSeconds()
        );

        loadHistoryFromDb();

        getServer().getScheduler().runTaskTimerAsynchronously(
                this, chatMessageRegistry::cleanup, 1200L, 1200L
        );

        getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    long cutoff = System.currentTimeMillis()
                            - (chatConfig.delete().ttlSeconds() * 1000L);
                    storageManager.cleanupHistory(cutoff);
                },
                20L * 60 * 5, 20L * 60 * 5
        );

        getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                () -> storageManager.cleanupExpiredIgnores(System.currentTimeMillis()),
                20L * 60, 20L * 60
        );

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
        getLogger().info("   Table prefix:   " + chatConfig.storage().tablePrefix());
        getLogger().info("");
        getLogger().info(" Features:");
        getLogger().info("   Channels:       " + channelManager.all().size());
        getLogger().info("   Rules:          " + ruleManager.size());
        getLogger().info("   Threads:        " + status(chatConfig.threads().enabled()));
        getLogger().info("   Player heads:   " + status(chatConfig.display().showPlayerHeads()));
        getLogger().info("   Head display:   " + status(chatConfig.display().headDisplayEnabled()));
        getLogger().info("   Emojis:         " + status(chatConfig.emoji().enabled()));
        getLogger().info("   PAPI expansion: " + status(getServer().getPluginManager().getPlugin("PlaceholderAPI") != null));
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

    private void registerPermissions() {
        var pm = getServer().getPluginManager();

        pm.addPermission(new Permission("terracraftchat.color",
                "Allows using &-codes and hex colors in messages",
                PermissionDefault.FALSE));
        pm.addPermission(new Permission("terracraftchat.msg",
                "Allows using /msg", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.reply",
                "Allows using /reply", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.ignore",
                "Allows using /ignore", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.thread",
                "Allows replying in threads", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.nick",
                "Allows using /nick", PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.emoji",
                "Allows using :emoji: in chat", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.item",
                "Allows using [item] in chat", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.inv",
                "Allows using [inv] in chat", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.inv.admin",
                "Allows /inv_show player <ник>", PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.broadcast",
                "Allows using /broadcast", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.admin",
                "Allows /chat reload", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.socialspy",
                "Allows seeing all private messages", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.moderator",
                "Allows deleting messages by clicking", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.channel.staff",
                "Allows using the staff channel", PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.stats",
                "Allows /chat stats", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.group.create",
                "Allows /chat create-group", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.group.use",
                "Allows /chat group <name> <msg>", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.group.invite",
                "Allows /chat invite", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.group.leave",
                "Allows /chat leave", PermissionDefault.TRUE));
        pm.addPermission(new Permission("terracraftchat.group.delete",
                "Allows /chat delete <group>", PermissionDefault.TRUE));

        pm.addPermission(new Permission("terracraftchat.bypass.ads",
                "Bypasses anti-advertisement", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.bypass.swear",
                "Bypasses swear filter", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.bypass.levenshtein",
                "Bypasses similarity check", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.bypass.flood",
                "Bypasses anti-flood", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.bypass.crash",
                "Bypasses anti-crash", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.bypass.cooldown",
                "Bypasses cooldown", PermissionDefault.OP));
        pm.addPermission(new Permission("terracraftchat.bypass.duplicate",
                "Bypasses duplicate check", PermissionDefault.OP));
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

            // Сброс кэша PAPI-статистики
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

    private void loadHistoryFromDb() {
        if (storageManager == null) return;

        long start = System.currentTimeMillis();
        long cutoff = System.currentTimeMillis()
                - (chatConfig.delete().ttlSeconds() * 1000L);
        int limit = chatConfig.delete().cacheSize();

        var rows = storageManager.loadRecentHistory(cutoff, limit);
        if (rows.isEmpty()) return;

        List<UUID> ids = rows.stream().map(StorageManager.HistoryRow::id).toList();
        var viewersByMsg = storageManager.loadViewersBatch(ids);
        var repliesByMsg = storageManager.getThreadRepliesBatch(ids);

        for (var row : rows) {
            ChatMessage msg = new ChatMessage(
                    row.id(), row.senderUuid(), row.senderName(),
                    row.rawMessage(), row.channelId()
            );

            var viewerIds = viewersByMsg.getOrDefault(row.id(), List.of());
            for (UUID v : viewerIds) msg.addViewer(v);

            var replies = repliesByMsg.getOrDefault(row.id(), List.of());
            for (var r : replies) {
                msg.addReply(new ChatMessage.ThreadReply(
                        r.id(), r.authorUuid(), r.authorName(),
                        r.text(), r.timestamp()
                ));
            }

            chatMessageRegistry.registerWithoutThreadLoad(msg);
        }

        getLogger().info("Загружено сообщений из истории: " + rows.size()
                + " за " + (System.currentTimeMillis() - start) + " мс");
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
        new ChatGamesCommand(this).register();   // ← добавили
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