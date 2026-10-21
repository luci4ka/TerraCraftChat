package ru.terracraft.chat.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public final class ChatConfig {

    @Comment("Форматы сообщений")
    private FormatsSection formats = new FormatsSection();

    @Comment("Каналы чата")
    private ChannelsSection channels = new ChannelsSection();

    @Comment("Треды (ответы на сообщения)")
    private ThreadSection threads = new ThreadSection();

    @Comment("Отображение: головы, сообщения над головой, [item], [inv]")
    private DisplaySection display = new DisplaySection();

    @Comment("Упоминания @Ник")
    private MentionsSection mentions = new MentionsSection();

    @Comment("Модерация: cooldown, дубли, капс, маты, реклама, anti-crash")
    private ModerationSection moderation = new ModerationSection();

    @Comment("RP-команды: /me, /do, /try, /roll")
    private RpSection rp = new RpSection();

    @Comment("Соц-шпион")
    private SocialSpySection socialSpy = new SocialSpySection();

    @Comment("Удаление сообщений модераторами")
    private DeleteSection delete = new DeleteSection();

    @Comment("Логирование чата")
    private LoggingSection logging = new LoggingSection();

    @Comment("Discord webhook")
    private DiscordSection discord = new DiscordSection();

    @Comment("Автообъявления")
    private AutobroadcastSection autobroadcast = new AutobroadcastSection();

    @Comment("Эмодзи")
    private EmojiSection emoji = new EmojiSection();

    @Comment("Локальные ответы бота")
    private LocalAnswersSection localAnswers = new LocalAnswersSection();

    @Comment("Chat Games (мини-игры)")
    private ChatGamesSection chatGames = new ChatGamesSection();

    @Comment("Хранилище данных (MineSQL)")
    private StorageSection storage = new StorageSection();

    // ============================================================
    //  ГЕТТЕРЫ
    // ============================================================

    public FormatsSection formats() { return formats; }
    public ChannelsSection channels() { return channels; }
    public ThreadSection threads() { return threads; }
    public DisplaySection display() { return display; }
    public MentionsSection mentions() { return mentions; }
    public ModerationSection moderation() { return moderation; }
    public RpSection rp() { return rp; }
    public SocialSpySection socialSpy() { return socialSpy; }
    public DeleteSection delete() { return delete; }
    public LoggingSection logging() { return logging; }
    public DiscordSection discord() { return discord; }
    public AutobroadcastSection autobroadcast() { return autobroadcast; }
    public EmojiSection emoji() { return emoji; }
    public LocalAnswersSection localAnswers() { return localAnswers; }
    public ChatGamesSection chatGames() { return chatGames; }
    public StorageSection storage() { return storage; }

    // ============================================================
    //  СЕКЦИЯ: ФОРМАТЫ
    // ============================================================

    @Configuration
    public static final class FormatsSection {
        @Comment({
                "Доступные плейсхолдеры:",
                "<sender_head>    — голова игрока (SkinsRestorer)",
                "<display_name>   — ник из плагина (или реальное имя)",
                "<sender_name>    — реальное имя игрока",
                "<world>          — мир отправителя",
                "<message>        — текст сообщения",
                "{msg_command}    — /msg <ник> (для клика)",
                "%luckperms_prefix% / %luckperms_suffix% — префикс/суффикс LuckPerms",
                "Любые PAPI-плейсхолдеры (%player_name%, ...)",
                "",
                "Формат локального сообщения"
        })
        private String local = "<gray>[<green>L<gray>] <click:suggest_command:'{msg_command}'>"
                + "<hover:show_text:'<gray>Нажми, чтобы написать'>"
                + "<sender_head> %luckperms_prefix%<display_name>%luckperms_suffix%"
                + "</hover></click><gray>: <reset><message>";

        @Comment("Формат глобального сообщения")
        private String global = "<gray>[<gold>G<gray>] <sender_head> %luckperms_prefix%<display_name>%luckperms_suffix%<gray>: <reset><message>";

        @Comment("Формат staff-канала")
        private String staff = "<dark_red>[<red>STAFF<dark_red>] <reset><sender_head> %luckperms_prefix%<display_name>%luckperms_suffix%<gray>: <reset><message>";

        @Comment("Формат /msg")
        private String privateMsg = "<gray>[<gold>ЛС<gray>] <sender_head> %luckperms_prefix%<sender>%luckperms_suffix% <dark_gray>→ <receiver_head> %luckperms_prefix%<receiver>%luckperms_suffix%<gray>: <white><message>";

        @Comment("Формат /reply")
        private String reply = "<gray>[<gold>ЛС<gray>] <sender_head> %luckperms_prefix%<sender>%luckperms_suffix% <dark_gray>→ <receiver_head> %luckperms_prefix%<receiver>%luckperms_suffix%<gray>: <white><message>";

        @Comment("Формат /broadcast")
        private String broadcast = "<dark_gray>[<red>Объявление<dark_gray>] <white><message>";

        @Comment("Сообщение, если рядом никого нет")
        private String noOneNearby = "<gray>[<yellow>Рядом никого не слышно<gray>]";

        public String local() { return local; }
        public String global() { return global; }
        public String staff() { return staff; }
        public String privateMsg() { return privateMsg; }
        public String reply() { return reply; }
        public String broadcast() { return broadcast; }
        public String noOneNearby() { return noOneNearby; }

        public String byKey(String key) {
            if (key == null) return local;
            return switch (key) {
                case "local"  -> local;
                case "global" -> global;
                case "staff"  -> staff;
                default       -> local;
            };
        }
    }

    // ============================================================
    //  СЕКЦИЯ: КАНАЛЫ
    // ============================================================

    @Configuration
    public static final class ChannelsSection {
        @Comment("Канал по умолчанию (id из channels)")
        private String defaultChannel = "local";

        @Comment("Радиус RP-чата (/me, /do, /try, /roll). 0 — всем.")
        private int rpRadius = 50;

        @Comment({
                "Список каналов. Ключ — id канала.",
                "prefix     — префикс в сообщении",
                "range      — радиус в блоках (-1 = вся карта/сеть)",
                "permission — право для доступа ('' = всем)",
                "format     — ключ из секции formats",
                "cooldown   — свой кулдаун в секундах (0 = общий)",
                "public     — в Discord, бот, упоминания."
        })
        private Map<String, ChannelEntry> channels = Map.of(
                "local",  new ChannelEntry("",  100, "",                              "local",  0, true),
                "global", new ChannelEntry("!", -1,  "",                              "global", 0, true),
                "staff",  new ChannelEntry("#", -1,  "terracraftchat.channel.staff",  "staff",  0, false)
        );

        public String defaultChannel() { return defaultChannel; }
        public int rpRadius() { return rpRadius; }
        public Map<String, ChannelEntry> channels() { return channels; }

        @Configuration
        public static class ChannelEntry {
            private String prefix = "";
            private int range = 100;
            private String permission = "";
            private String format = "local";
            private int cooldown = 0;

            @Comment("Публичный канал (Discord, бот, упоминания).")
            private boolean publicChannel = false;

            public ChannelEntry() {}

            public ChannelEntry(String prefix, int range, String permission,
                                String format, int cooldown, boolean publicChannel) {
                this.prefix = prefix;
                this.range = range;
                this.permission = permission;
                this.format = format;
                this.cooldown = cooldown;
                this.publicChannel = publicChannel;
            }

            public String prefix() { return prefix; }
            public int range() { return range; }
            public String permission() { return permission; }
            public String format() { return format; }
            public int cooldown() { return cooldown; }
            public boolean publicChannel() { return publicChannel; }
        }
    }

    // ============================================================
    //  СЕКЦИЯ: ТРЕДЫ
    // ============================================================

    @Configuration
    public static final class ThreadSection {
        @Comment("Включить треды (ответы на сообщения)")
        private boolean enabled = true;

        @Comment("Сколько ответов показывать инлайн под родителем")
        private int inlineThreadSize = 3;

        @Comment("Максимум ответов в треде")
        private int maxThreadReplies = 20;

        public boolean enabled() { return enabled; }
        public int inlineThreadSize() { return inlineThreadSize; }
        public int maxThreadReplies() { return maxThreadReplies; }
    }

    // ============================================================
    //  СЕКЦИЯ: ОТОБРАЖЕНИЕ
    // ============================================================

    @Configuration
    public static final class DisplaySection {
        @Comment("Показывать сообщения над головой")
        private boolean headDisplayEnabled = true;

        @Comment("Длительность (сек)")
        private int headDisplayDuration = 3;

        @Comment("Показывать голову игрока в чате")
        private boolean showPlayerHeads = true;

        @Comment("Разрешить [item] в сообщениях")
        private boolean itemPlaceholderEnabled = true;

        @Comment("Разрешить [inv] в сообщениях")
        private boolean invPlaceholderEnabled = true;

        @Comment("Максимум [item] в одном сообщении")
        private int maxItemPlaceholders = 3;

        @Comment("Максимум [inv] в одном сообщении")
        private int maxInvPlaceholders = 1;

        @Comment("Заголовок GUI инвентаря")
        private String invGuiTitle = "Инвентарь: <player>";

        @Comment("Время жизни токена [inv] (сек)")
        private int invTokenTtlSeconds = 60;

        public boolean headDisplayEnabled() { return headDisplayEnabled; }
        public int headDisplayDuration() { return headDisplayDuration; }
        public boolean showPlayerHeads() { return showPlayerHeads; }
        public boolean itemPlaceholderEnabled() { return itemPlaceholderEnabled; }
        public boolean invPlaceholderEnabled() { return invPlaceholderEnabled; }
        public int maxItemPlaceholders() { return maxItemPlaceholders; }
        public int maxInvPlaceholders() { return maxInvPlaceholders; }
        public String invGuiTitle() { return invGuiTitle; }
        public int invTokenTtlSeconds() { return invTokenTtlSeconds; }
    }

    // ============================================================
    //  СЕКЦИЯ: УПОМИНАНИЯ
    // ============================================================

    @Configuration
    public static final class MentionsSection {
        @Comment("Звук при упоминании (пусто — отключено)")
        private String sound = "minecraft:entity.experience_orb.pickup";

        @Comment("Подсветка упоминания")
        private String highlight = "<yellow>@<player></yellow>";

        public String sound() { return sound; }
        public String highlight() { return highlight; }
    }

    // ============================================================
    //  СЕКЦИЯ: МОДЕРАЦИЯ
    // ============================================================

    @Configuration
    public static final class ModerationSection {
        @Comment("Cooldown между сообщениями (сек, 0 — отключено)")
        private int cooldown = 3;

        @Comment("Блокировать повтор одного и того же сообщения")
        private boolean antiDuplicate = true;

        @Comment("Максимальная доля заглавных букв (0.0 - 1.0)")
        private double maxCapsRatio = 0.7;

        @Comment("Минимальная длина для проверки капса")
        private int minCapsLength = 6;

        @Comment("Проверка похожести (Levenshtein)")
        private boolean levenshteinEnabled = true;

        @Comment("Порог похожести (0.0 - 1.0)")
        private double levenshteinThreshold = 0.85;

        @Comment("Минимальная длина для Levenshtein")
        private int levenshteinMinLength = 5;

        @Comment("Максимум одинаковых символов подряд (0 — отключено)")
        private int maxRepeatingChars = 5;

        @Comment("Anti-crash: блокировать control-символы")
        private boolean antiCrashEnabled = true;

        @Comment("Фильтр мата")
        private boolean swearFilterEnabled = true;

        @Comment("Запрещённые слова")
        private List<String> swearWords = List.of("хуй", "пизда", "блядь", "ебать", "сука");

        @Comment("Замена мата")
        private String swearReplacement = "***";

        @Comment("Максимум матов в сообщении (0 — блок при первом)")
        private int maxSwearWords = 2;

        @Comment("Заменять мат на *** вместо блокировки")
        private boolean swearReplaceInsteadOfBlock = true;

        @Comment("Блокировать IP-адреса")
        private boolean antiIpEnabled = true;

        @Comment("Блокировать ссылки")
        private boolean antiUrlEnabled = true;

        @Comment("Блокировать домены")
        private boolean antiDomainEnabled = true;

        @Comment("Разрешённые домены")
        private List<String> allowedDomains = List.of(
                "terracraft.net.ru",
                "discord.gg/terracraft"
        );

        public int cooldown() { return cooldown; }
        public boolean antiDuplicate() { return antiDuplicate; }
        public double maxCapsRatio() { return maxCapsRatio; }
        public int minCapsLength() { return minCapsLength; }
        public boolean levenshteinEnabled() { return levenshteinEnabled; }
        public double levenshteinThreshold() { return levenshteinThreshold; }
        public int levenshteinMinLength() { return levenshteinMinLength; }
        public int maxRepeatingChars() { return maxRepeatingChars; }
        public boolean antiCrashEnabled() { return antiCrashEnabled; }
        public boolean swearFilterEnabled() { return swearFilterEnabled; }
        public List<String> swearWords() { return swearWords; }
        public String swearReplacement() { return swearReplacement; }
        public int maxSwearWords() { return maxSwearWords; }
        public boolean swearReplaceInsteadOfBlock() { return swearReplaceInsteadOfBlock; }
        public boolean antiIpEnabled() { return antiIpEnabled; }
        public boolean antiUrlEnabled() { return antiUrlEnabled; }
        public boolean antiDomainEnabled() { return antiDomainEnabled; }
        public List<String> allowedDomains() { return allowedDomains; }
    }

    // ============================================================
    //  СЕКЦИЯ: RP
    // ============================================================

    @Configuration
    public static final class RpSection {
        @Comment("Формат /me")
        private String meFormat = "<gray>* <white>%luckperms_prefix%<player>%luckperms_suffix% <gray><message>";

        @Comment("Формат /do")
        private String doFormat = "<gray>* <message>";

        @Comment("Формат /try")
        private String tryFormat = "<gray>* <white>%luckperms_prefix%<player>%luckperms_suffix% <gray>попытался <message> <result>";

        @Comment("Успех /try")
        private String trySuccess = "<green>и у него получилось!";

        @Comment("Провал /try")
        private String tryFail = "<red>и у него не вышло...";

        @Comment("Формат /roll")
        private String rollFormat = "<gray>* <white>%luckperms_prefix%<player>%luckperms_suffix% <gray>бросил кубик: <yellow><result> <gray>(1-<max>)";

        public String meFormat() { return meFormat; }
        public String doFormat() { return doFormat; }
        public String tryFormat() { return tryFormat; }
        public String trySuccess() { return trySuccess; }
        public String tryFail() { return tryFail; }
        public String rollFormat() { return rollFormat; }
    }

    // ============================================================
    //  СЕКЦИЯ: СОЦ-ШПИОН
    // ============================================================

    @Configuration
    public static final class SocialSpySection {
        @Comment("Включить соц-шпион (право terracraftchat.socialspy)")
        private boolean enabled = true;

        @Comment("Формат сообщения шпиона")
        private String format = "<dark_gray>[<red>SPY<dark_gray>] <gray>[<white><type><gray>] %luckperms_prefix%<sender>%luckperms_suffix% <dark_gray>→ %luckperms_prefix%<receiver>%luckperms_suffix%<gray>: <white><message>";

        public boolean enabled() { return enabled; }
        public String format() { return format; }
    }

    // ============================================================
    //  СЕКЦИЯ: УДАЛЕНИЕ СООБЩЕНИЙ
    // ============================================================

    @Configuration
    public static final class DeleteSection {
        @Comment("Разрешить удаление (право terracraftchat.moderator)")
        private boolean enabled = true;

        @Comment("Размер кэша сообщений")
        private int cacheSize = 200;

        @Comment("TTL сообщения (сек)")
        private int ttlSeconds = 300;

        @Comment("Кнопка удаления")
        private String button = "<dark_red>[✖]</dark_red>";

        @Comment("Сколько строк 'сдвинуть' при redraw после удаления (как Allium)")
        private int clearLines = 120;

        public boolean enabled() { return enabled; }
        public int cacheSize() { return cacheSize; }
        public int ttlSeconds() { return ttlSeconds; }
        public String button() { return button; }
        public int clearLines() { return clearLines; }
    }

    // ============================================================
    //  СЕКЦИЯ: ЛОГИРОВАНИЕ
    // ============================================================

    @Configuration
    public static final class LoggingSection {
        @Comment("Логировать чат в logs/chat-YYYY-MM-DD.log")
        private boolean enabled = true;

        @Comment("Логировать текст личных сообщений")
        private boolean logPrivate = false;

        public boolean enabled() { return enabled; }
        public boolean logPrivate() { return logPrivate; }
    }

    // ============================================================
    //  СЕКЦИЯ: DISCORD
    // ============================================================

    @Configuration
    public static final class DiscordSection {
        @Comment("Отправлять глобальный чат в Discord")
        private boolean enabled = false;

        @Comment("URL webhook'а")
        private String webhookUrl = "";

        @Comment("Имя бота")
        private String username = "TerraCraft Chat";

        @Comment("Шаблон: %player%, %message%, %world%")
        private String format = "**[%world%] %player%:** %message%";

        @Comment("Префикс для /broadcast в Discord")
        private String broadcastPrefix = "**[Объявление]**";

        @Comment("Интервал отправки буфера (сек)")
        private int intervalSeconds = 5;

        @Comment("Максимум сообщений в буфере")
        private int maxBuffer = 50;

        public boolean enabled() { return enabled; }
        public String webhookUrl() { return webhookUrl; }
        public String username() { return username; }
        public String format() { return format; }
        public String broadcastPrefix() { return broadcastPrefix; }
        public int intervalSeconds() { return intervalSeconds; }
        public int maxBuffer() { return maxBuffer; }
    }

    // ============================================================
    //  СЕКЦИЯ: АВТООБЪЯВЛЕНИЯ
    // ============================================================

    @Configuration
    public static final class AutobroadcastSection {
        @Comment("Интервал (сек)")
        private int interval = 300;

        @Comment("Перемешивать порядок")
        private boolean random = false;

        @Comment({
                "Список сообщений.",
                "Можно указывать либо ключ перевода (например, autobroadcast.rules),",
                "либо готовую строку с MiniMessage/&-кодами."
        })
        private List<String> messages = List.of(
                "autobroadcast.rules",
                "autobroadcast.discord",
                "autobroadcast.donate"
        );

        public int interval() { return interval; }
        public boolean random() { return random; }
        public List<String> messages() { return messages; }
    }

    // ============================================================
    //  СЕКЦИЯ: ЭМОДЗИ
    // ============================================================

    @Configuration
    public static final class EmojiSection {
        @Comment("Разрешить :код: эмодзи (право terracraftchat.emoji)")
        private boolean enabled = true;

        public boolean enabled() { return enabled; }
    }

    // ============================================================
    //  СЕКЦИЯ: ЛОКАЛЬНЫЕ ОТВЕТЫ
    // ============================================================

    @Configuration
    public static final class LocalAnswersSection {
        @Comment("Включить локальные ответы")
        private boolean enabled = true;

        @Comment("Префикс ответа")
        private String prefix = "<gray>[<gold>Бот<gray>] <reset>";

        @Comment("Кулдаун между ответами бота (сек)")
        private int cooldownSeconds = 10;

        @Comment("Ключ — регулярка, значение — ответ")
        private Map<String, String> answers = Map.of(
                "как скрафтить наковальню", "3 блока железа + 4 слитка железа",
                "где спавн", "Спавн в центре карты, координаты 0, 64, 0"
        );

        public boolean enabled() { return enabled; }
        public String prefix() { return prefix; }
        public int cooldownSeconds() { return cooldownSeconds; }
        public Map<String, String> answers() { return answers; }
    }

    // ============================================================
    //  СЕКЦИЯ: CHAT GAMES
    // ============================================================

    @Configuration
    public static final class ChatGamesSection {
        @Comment("Включить мини-игры")
        private boolean enabled = true;

        @Comment("Интервал между играми (сек)")
        private int interval = 300;

        @Comment("Минимум игроков онлайн")
        private int minPlayers = 3;

        @Comment("Длительность игры (сек)")
        private int duration = 30;

        @Comment("Файл с контентом игр")
        private String gamesFile = "games.yml";

        public boolean enabled() { return enabled; }
        public int interval() { return interval; }
        public int minPlayers() { return minPlayers; }
        public int duration() { return duration; }
        public String gamesFile() { return gamesFile; }
    }

    // ============================================================
    //  СЕКЦИЯ: STORAGE (MineSQL)
    // ============================================================

    @Configuration
    public static final class StorageSection {
        @Comment({
                "Префикс таблиц в общей БД.",
                "",
                "Сама БД настраивается в plugins/MineSQL/config.yml,",
                "в секции с ID 'terracraft-chat':",
                "",
                "  databases:",
                "    \"terracraft-chat\":",
                "      driver-type: mariadb",
                "      host: 127.0.0.1",
                "      port: 3306",
                "      database: terra_core",
                "      username: terra",
                "      password: ...",
                "",
                "Используется общий пул соединений MineSQL.",
                "Изменения в plugins/MineSQL/config.yml требуют /minesql reload или рестарта."
        })
        private String tablePrefix = "tc_";

        public String tablePrefix() { return tablePrefix; }
    }
}