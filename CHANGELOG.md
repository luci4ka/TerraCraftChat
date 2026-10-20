# Changelog

Все значимые изменения проекта документируются в этом файле.

Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
версионирование — [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]

### Планируется
- Discord → игра (через бота, а не webhook)
- Дополнительные языки (uk, be, kk)
- Тесты (MockBukkit)
- Multiple-choice и reaction Chat Games
- Крос-серверность (Redis Pub/Sub)
- PacketEvents-интеграция для «настоящего» удаления без перерисовки

---

## [1.1.0] - 2026-10-20

### Добавлено
- **Гибкие каналы**: `channels.<id>` в `config.yml` (`prefix`, `range`, `permission`, `format`, `cooldown`, `public-channel`)
- **Staff-канал** (`#`) с защитой от утечки в Discord/бота/упоминания
- **`/channel <id>`** и **`/channel reset`** — переключение активного канала
- **`[inv]`-токены**: одноразовые токены с TTL (60 сек), привязаны к `(target, viewer)`
- **`/inv_show player <ник>`** — админский просмотр инвентаря через `PlayerProfileArgument`
- **Перерисовка чата** при удалении (`ChatRedrawManager`) — работает для онлайн **и** офлайн
- **PAPI-расширение** `%terracraftchat_...%`:
    - `nick_formatted` / `nick_mm` — ник с MiniMessage-тегами (для таба)
    - `nick` — plain text
    - `displayname_formatted` / `displayname`
    - `channel`, `channel_prefix`
    - `ignoring_count`, `socialspy`, `thread_active`
    - `channels`, `threads_enabled`, `games_count` (серверные)
- **`NickFormat`** — безопасный `MiniMessage` для ников (только цвета, без `click`/`hover`/`insertion`)
- **`/nickreset`** — сброс ника
- **Имперсонация в `/nick`** — запрет ников, совпадающих с реальными именами/никами онлайн-игроков
- **Зарезервированные ники** (`admin`, `console`, `staff`, ...)
- **`/ignore list`** — список игнорируемых
- **Игнор офлайн-игроков** через `PlayerProfileArgument` (UUID-API в `IgnoreManager`)
- **`ShuffleBag`** для Chat Games — слова/вопросы не повторяются, пока не кончатся
- **Per-game `timeout`** в `games.yml`
- **Cooldown на неправильные ответы** в Chat Games (3 сек)
- **`[item]`/`[inv]` лимиты** на сообщение (`maxItemPlaceholders`, `maxInvPlaceholders`)
- **`public-channel`** флаг канала (по умолчанию `false` для безопасности)
- **Overlay локалей**: jar-база + `lang/messages_*.yml` игрока (новые ключи всегда есть)
- **`Locale.ROOT`** в `toLowerCase`/`toUpperCase` везде
- **`volatile`** в `ChatMessageRegistry`, `ChatFormatter`, `ChatModerator`, `ChannelManager`
- **`unmodifiableSet`** в `SocialSpyManager.getAll()` и `IgnoreManager.getIgnoredByUuid()`
- **`chat.discord-format`** — шаблон для `/broadcast` в Discord
- **`broadcast-prefix`** в `DiscordSection`
- **`nick-too-long`** — синхронизирован с `MAX_VISIBLE_LENGTH` (16)

### Изменено
- **`ChatListener`**: порядок «модерация → игра → тред → чат»
- **`ChatListener`**: `record()` в async-фазе (закрывает гонку cooldown)
- **`ChatListener`**: `collectViewers` учитывает `canUse`, ignore, vanish
- **`ChatModerator`**: `Bypass` как снапшот прав (async-safe)
- **`ChatModerator`**: `reload()` перекомпилирует паттерны **без** сброса cooldown
- **`ChatModerator`**: NFKC + замена латинских омоглифов + удаление пробелов между буквами
- **`ChatModerator`**: `Matcher.quoteReplacement` для `$` в замене
- **`ChatModerator`**: `IP_PATTERN` больше не блокирует `2026`/`1500`
- **`ChatModerator`**: whitelist вырезает домены и проверяет остаток
- **`ChatModerator`**: `DOMAIN_PATTERN` требует 3+ символа в TLD
- **`ChatModerator`**: нормализация `evil . com` → `evil.com`
- **`ChatFormatter`**: `[inv]` не парсится в копии отправителя (только в копии получателя)
- **`ChatFormatter`**: `formatBroadcast` с `hexColors()`
- **`ChatFormatter`**: `applyPapi` в один проход (по `sender`)
- **`ChatLogger`**: асинхронная запись, ротация по датам, санитизация `\n`/`\r`
- **`ChatLogger`**: NPE-фикс при `close()` во время `flush()`
- **`ThreadManager`**: `threads().enabled()` — первая проверка
- **`ThreadManager`**: `wasSeenBy`, `maxThreadReplies`, ignore-лист, лог
- **`ThreadManager`**: запрет ответа на своё сообщение (по UUID)
- **`TranslationManager`**: fallback `en → ru`, `shutdown()` в `onDisable`
- **`TranslationManager`**: `pt_BR` → `pt-BR` (дефис)
- **`DiscordWebhook`**: `allowed_mentions.parse = []` (анти-`@everyone`)
- **`DiscordWebhook`**: возврат в буфер при 429 + `Retry-After`
- **`DiscordWebhook`**: Gson вместо ручной сборки JSON
- **`AutoBroadcast`**: `reload(ChatConfig)`
- **`LocalAnswerManager`**: `LinkedHashMap`, кулдаун, `Locale.ROOT`
- **`ReplyManager`, `SocialSpyManager`, `SkinHeadCache`**: `ConcurrentHashMap`
- **`IgnoreManager`**: UUID-API, `addIgnore(Player, UUID)`, `unmodifiableSet`
- **`HeadDisplayManager`**: следование за игроком (`teleportDuration` + `runTaskTimer`)
- **`HeadDisplayManager`**: `hideAllFrom(Player)` для новых игроков
- **`DeleteCommand`**: убрана лишняя пустая строка, `delete-disabled` проверка
- **`BroadcastCommand`**: алиас `tcbroadcast`, Discord-интеграция
- **`MsgCommand`, `ReplyCommand`**: взаимная проверка ignore (обе стороны)
- **`RpCommand`**: ignore-лист для `/me`, `/do`, `/try`, `/roll`
- **`ChannelCommand`**: `buildFuture()` для suggestions
- **`InvCommand`**: токен без `invalidate` (TTL 60 сек), логирование `INVSEE`
- **`paper-plugin.yml`**: `SkinsRestorer` — `required: false`
- **`paper-plugin.yml`**: `CommandAPI` — `join-classpath: true`
- **`paper-plugin.yml`**: `PlaceholderAPI` — `join-classpath: true`
- **`build.gradle`**: `targetJavaVersion = 21` (было 25 — Paper 1.21.11 требует 21)
- **`build.gradle`**: PacketEvents `2.11.0` (было `2.7.0` — не поддерживает 1.21.11)
- **`build.gradle`**: `group = 'ru.net.terracraft'`, `version = '1.0.0'`
- **`settings.gradle`**: `rootProject.name = 'Chat'`

### Исправлено
- **Утечка staff-канала** в Discord, бот, упоминания (`Channel.isPublic()` + `collectViewers`)
- **XSS через ник** (`<click:run_command:/op ...>` в `/nick`) — `NickFormat.SAFE`
- **`/inv_show` без права** — токены + `terracraftchat.inv.admin`
- **Цензура мата не работала** — `SWEAR` теперь пропускается в чат
- **Гонка cooldown** — `record()` в async
- **Discord `@everyone`** — `allowed_mentions`
- **Антиреклама ловила обычный текст** (`out. Games` → `out.Games`)
- **Фильтр мата вешал async-поток** (пустая строка в `swearWords`)
- **`(?i)` без `u`** не сворачивал кириллицу
- **`$` в `swearReplacement`** кидал `IllegalArgumentException`
- **Подстрока ловила `хлебать` как `ебать`** — границы слова
- **`reload()` не перезагружал всё** — `reload(ChatConfig)` во всех менеджерах
- **`/chat_delete` не смотрел на `delete.enabled`**
- **Треды** обходили модерацию (порядок в `ChatListener`)
- **Треды** — `maxThreadReplies`, `wasSeenBy`, ignore, лог
- **`/chat reload`** сообщал об успехе при ошибке YAML — `boolean reload()`
- **`publicChannel` по умолчанию `true`** → утечка staff — дефолт `false` + `permission.isEmpty()`
- **`/inv_show player <ник>`** не работал (Brigadier выбирал токен) — `withSubcommand`
- **Игрок «застревал»** в канале без права — сброс активного канала
- **`ChannelManager.load`** не атомарен — `volatile` + `LinkedHashMap` + сортировка префиксов
- **NPE в `ChatPlaceholders`** на `getNickComponent` — null-check
- **NPE в `ChatLogger.flush`** при `close()` — локальная копия `BufferedWriter`

### Удалено
- `SpriteResolver` (ломал `<gradient:red:gold>`)
- Мёртвые ключи из `messages_*.yml`: `player-not-found`, `already-ignored`, `not-ignored`, `reaction-invalid`, `no-permission`
- `settings:` секция из `games.yml` (не использовалась)
- Дубликаты `new CommandAPICommand("inv_show")` и `new CommandAPICommand("channel")`

---

## [1.0.0] - 2026-10-11

### Добавлено
- Локальный и глобальный чат
- ЛС `/msg`, `/reply`
- Модерация (cooldown, дубли, маты, реклама, anti-crash)
- Эмодзи, `[item]`, `[inv]`
- Головы игроков через SkinsRestorer
- Соц-шпион
- Broadcast
- Логирование
- Мультиязычность (ru, en, de)
- Треды (ответы на сообщения)
- Chat Games (Unscramble, Quiz, Math)
- AutoBroadcast с i18n
- Проверка обновлений