package ru.net.terracraft.chat;

import de.exlll.configlib.YamlConfigurations;
import org.bstats.bukkit.Metrics;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.command.*;
import ru.net.terracraft.chat.config.ChatConfig;
import ru.net.terracraft.chat.game.GamesConfig;

import java.nio.file.Path;

public final class TerraCraftChatPlugin extends JavaPlugin {

    private static TerraCraftChatPlugin instance;

    // ANSI цвета для логов
    private static final String RESET   = "\u001B[0m";
    private static final String BOLD    = "\u001B[1m";
    private static final String DIM     = "\u001B[2m";
    private static final String RED     = "\u001B[31m";
    private static final String GREEN   = "\u001B[32m";
    private static final String YELLOW  = "\u001B[33m";
    private static final String BLUE    = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN    = "\u001B[36m";
    private static final String WHITE   = "\u001B[37m";
    private static final String GRAY    = "\u001B[90m";

    // ============================================================
    //  КОНФИГ И МЕНЕДЖЕРЫ
    // ============================================================

    private ChatConfig chatConfig;

    private TranslationManager translationManager;
    private SkinHeadCache skinHeadCache;
    private EmojiManager emojiManager;
    private GamesConfig gamesConfig;
    private IgnoreManager ignoreManager;
    private ThreadManager threadManager;
    private ReplyManager replyManager;
    private SocialSpyManager socialSpyManager;
    private ChatModerator chatModerator;
    private LocalAnswerManager localAnswerManager;
    private ChatMessageRegistry chatMessageRegistry;
    private UpdateNotifier updateNotifier;
    private ChatGameManager chatGameManager;
    private ChatLogger chatLogger;
    private DiscordWebhook discordWebhook;
    private AutoBroadcast autoBroadcast;
    private HeadDisplayManager headDisplayManager;
    private ChatFormatter chatFormatter;

    // ============================================================
    //  ЖИЗНЕННЫЙ ЦИКЛ
    // ============================================================

    @Override
    public void onEnable() {
        instance = this;

        // ============================================================
        //  0. BSTATS
        // ============================================================
        int pluginId = 12345; // ← замени на свой ID
        new Metrics(this, pluginId);

        registerPermissions();
        loadConfigs();

        // ============================================================
        //  1. МЕНЕДЖЕРЫ БЕЗ ЗАВИСИМОСТЕЙ
        // ============================================================
        this.translationManager = new TranslationManager(this);
        this.translationManager.loadAll();

        this.skinHeadCache = new SkinHeadCache(this);

        this.emojiManager = new EmojiManager(this);
        this.emojiManager.load();

        this.gamesConfig = new GamesConfig(this);
        this.gamesConfig.load();

        this.ignoreManager = new IgnoreManager(this);
        this.replyManager = new ReplyManager(this);
        this.socialSpyManager = new SocialSpyManager(this);

        // ============================================================
        //  2. МОДЕРАЦИЯ И ЛОКАЛЬНЫЕ ОТВЕТЫ
        // ============================================================
        this.chatModerator = new ChatModerator(this, chatConfig);
        this.localAnswerManager = new LocalAnswerManager(this, chatConfig);

        // ============================================================
        //  3. ХРАНИЛИЩА
        // ============================================================
        this.chatMessageRegistry = new ChatMessageRegistry(
                this,
                chatConfig.delete().cacheSize(),
                chatConfig.delete().ttlSeconds()
        );

        // ============================================================
        //  4. ФОРМАТТЕР И ОТОБРАЖЕНИЕ
        // ============================================================
        this.chatFormatter = new ChatFormatter(this, chatConfig);
        this.headDisplayManager = new HeadDisplayManager(
                this,
                chatConfig.display().headDisplayDuration()
        );

        // ============================================================
        //  5. THREAD MANAGER
        // ============================================================
        this.threadManager = new ThreadManager(this, chatMessageRegistry, chatFormatter);

        // ============================================================
        //  6. CHAT GAME MANAGER
        // ============================================================
        this.chatGameManager = new ChatGameManager(this, chatConfig, gamesConfig);

        // ============================================================
        //  7. ВНЕШНИЕ ИНТЕГРАЦИИ
        // ============================================================
        this.discordWebhook = new DiscordWebhook(this, chatConfig);
        this.discordWebhook.start();

        this.autoBroadcast = new AutoBroadcast(this, chatConfig);
        this.autoBroadcast.start();

        // ============================================================
        //  8. ЛОГИРОВАНИЕ
        // ============================================================
        if (chatConfig.logging().enabled()) {
            this.chatLogger = new ChatLogger(this);
            this.chatLogger.open();
        }

        // ============================================================
        //  9. СЛУШАТЕЛИ
        // ============================================================
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        getServer().getPluginManager().registerEvents(new InvListener(this), this);
        getServer().getPluginManager().registerEvents(
                new ChatListener(this, ignoreManager, headDisplayManager, chatFormatter), this
        );

        // ============================================================
        //  10. КОМАНДЫ
        // ============================================================
        new RpCommand(this).register();
        registerCommands();

        // ============================================================
        //  11. ЗАПУСК ЗАДАЧ
        // ============================================================
        this.chatGameManager.start();

        // ============================================================
        //  12. ПРОВЕРКА ОБНОВЛЕНИЙ
        // ============================================================
        this.updateNotifier = new UpdateNotifier(this);
        this.updateNotifier.checkAsync();

        printStartupBanner();
    }

    @Override
    public void onDisable() {
        if (headDisplayManager != null) headDisplayManager.shutdown();
        if (chatLogger != null) chatLogger.close();
        if (chatGameManager != null) chatGameManager.stop();
        if (autoBroadcast != null) autoBroadcast.stop();
        if (discordWebhook != null) discordWebhook.stop();

        printShutdownBanner();
    }

    // ============================================================
    //  КРАСИВЫЙ БАННЕР
    // ============================================================

    private void printStartupBanner() {
        String line = GRAY + "══════════════════════════════════════════════════════" + RESET;

        log("");
        log(line);
        log("  " + BOLD + CYAN + "TerraCraftChat" + RESET + " " + GREEN + "успешно загружен!" + RESET + " 🎉");
        log("  " + GRAY + "Версия:" + RESET + " " + WHITE + getPluginMeta().getVersion() + RESET
                + " " + GRAY + "by" + RESET + " " + MAGENTA + "luci" + RESET + " 👨‍💻");
        log("  " + GRAY + "Сервер:" + RESET + " " + WHITE + getServer().getName() + " " + getServer().getVersion() + RESET);
        log("");

        // Зависимости
        log("  " + BOLD + YELLOW + "Зависимости:" + RESET);
        log("    " + GRAY + "CommandAPI:    " + RESET + status(getServer().getPluginManager().getPlugin("CommandAPI")));
        log("    " + GRAY + "PlaceholderAPI:" + RESET + " " + status(getServer().getPluginManager().getPlugin("PlaceholderAPI")));
        log("    " + GRAY + "SkinsRestorer: " + RESET + status(getServer().getPluginManager().getPlugin("SkinsRestorer")));
        log("");

        // Фичи
        log("  " + BOLD + YELLOW + "Фичи:" + RESET);
        log("    " + GRAY + "Треды:                 " + RESET + status(chatConfig.threads().enabled()));
        log("    " + GRAY + "Головы в чате:         " + RESET + status(chatConfig.display().showPlayerHeads()));
        log("    " + GRAY + "Сообщения над головой: " + RESET + status(chatConfig.display().headDisplayEnabled()));
        log("    " + GRAY + "Эмодзи:                " + RESET + status(chatConfig.emoji().enabled()));
        log("    " + GRAY + "Discord:               " + RESET + status(chatConfig.discord().enabled()));
        log("    " + GRAY + "AutoBroadcast:         " + RESET + status(autoBroadcast != null));
        log("    " + GRAY + "Chat Games:            " + RESET + status(chatConfig.chatGames().enabled()));
        log("    " + GRAY + "Логирование:           " + RESET + status(chatConfig.logging().enabled()));
        log("");

        // Статистика
        log("  " + BOLD + YELLOW + "Загружено:" + RESET);
        log("    " + GRAY + "Локалей:  " + RESET + WHITE + translationManager.getLocaleCount() + RESET);
        log("    " + GRAY + "Эмодзи:   " + RESET + WHITE + emojiManager.size() + RESET);
        log("    " + GRAY + "Игр:      " + RESET + WHITE + gamesConfig.getGames().size() + RESET);
        log(line);
        log("");
    }

    private void printShutdownBanner() {
        String line = GRAY + "══════════════════════════════════════════════════════" + RESET;

        log("");
        log(line);
        log("  " + BOLD + CYAN + "TerraCraftChat" + RESET + " " + RED + "выключен." + RESET + " До встречи! 👋");
        log(line);
        log("");
    }

    private String status(Plugin plugin) {
        if (plugin == null) return RED + "❌ не найден" + RESET;
        if (!plugin.isEnabled()) return YELLOW + "⚠ выключен" + RESET;
        return GREEN + "✅ v" + plugin.getPluginMeta().getVersion() + RESET;
    }

    private String status(boolean enabled) {
        return enabled ? GREEN + "✅" + RESET : RED + "❌" + RESET;
    }

    private void log(String message) {
        getServer().getConsoleSender().sendMessage(message);
    }

    // ============================================================
    //  ПРАВА
    // ============================================================

    private void registerPermissions() {
        var pm = getServer().getPluginManager();

        // --- Основные ---
        pm.addPermission(new Permission("terracraftchat.color",
                "Разрешает использовать &-коды и hex-цвета в сообщениях",
                PermissionDefault.FALSE));

        pm.addPermission(new Permission("terracraftchat.msg",
                "Разрешает использовать /msg",
                PermissionDefault.TRUE));

        pm.addPermission(new Permission("terracraftchat.reply",
                "Разрешает использовать /reply",
                PermissionDefault.TRUE));

        pm.addPermission(new Permission("terracraftchat.ignore",
                "Разрешает использовать /ignore",
                PermissionDefault.TRUE));

        // --- Контент ---
        pm.addPermission(new Permission("terracraftchat.emoji",
                "Разрешает использовать :эмодзи: в чате",
                PermissionDefault.TRUE));

        pm.addPermission(new Permission("terracraftchat.item",
                "Разрешает использовать [item] в чате",
                PermissionDefault.TRUE));

        pm.addPermission(new Permission("terracraftchat.inv",
                "Разрешает использовать [inv] в чате",
                PermissionDefault.TRUE));

        // --- Админ ---
        pm.addPermission(new Permission("terracraftchat.broadcast",
                "Разрешает использовать /broadcast",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.admin",
                "Разрешает /chat reload",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.socialspy",
                "Разрешает видеть все личные сообщения",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.moderator",
                "Разрешает удалять сообщения кликом",
                PermissionDefault.OP));

        // --- Bypass ---
        pm.addPermission(new Permission("terracraftchat.bypass.ads",
                "Обход анти-рекламы",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.bypass.swear",
                "Обход фильтра мата",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.bypass.levenshtein",
                "Обход проверки похожести",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.bypass.flood",
                "Обход анти-флуда",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.bypass.crash",
                "Обход anti-crash",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.bypass.cooldown",
                "Обход cooldown",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.bypass.duplicate",
                "Обход анти-дубля",
                PermissionDefault.OP));

        pm.addPermission(new Permission("terracraftchat.thread",
                "Разрешает отвечать в треды",
                PermissionDefault.TRUE));
    }

    // ============================================================
    //  КОНФИГ
    // ============================================================

    public void loadConfigs() {
        Path dataFolder = getDataFolder().toPath();
        Path configPath = dataFolder.resolve("config.yml");
        this.chatConfig = YamlConfigurations.update(configPath, ChatConfig.class);
    }

    public void reload() {
        loadConfigs();

        if (autoBroadcast != null) autoBroadcast.reload();

        this.gamesConfig.load();
        this.chatGameManager.stop();
        this.chatGameManager = new ChatGameManager(this, chatConfig, gamesConfig);
        this.chatGameManager.start();

        this.emojiManager.load();
        this.chatFormatter = new ChatFormatter(this, chatConfig);
        this.translationManager.reload();
    }

    // ============================================================
    //  КОМАНДЫ
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
    }

    // ============================================================
    //  ГЕТТЕРЫ
    // ============================================================

    public static TerraCraftChatPlugin getInstance() { return instance; }

    public ChatConfig getChatConfig() { return chatConfig; }
    public ChatFormatter getChatFormatter() { return chatFormatter; }
    public ChatLogger getChatLogger() { return chatLogger; }
    public ChatMessageRegistry getChatMessageRegistry() { return chatMessageRegistry; }
    public ChatModerator getChatModerator() { return chatModerator; }
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
}