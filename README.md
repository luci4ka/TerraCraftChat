
<div align="center">

# TerraCraftChat

**Современный чат-плагин для Paper 1.21.11**

[![Paper](https://img.shields.io/badge/Paper-1.21.11-blue?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25+-orange?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

Локальный и глобальный чат, ЛС, треды, модерация, эмодзи, головы игроков, Discord-мост, группы, статистика и многое другое.

</div>

---

## ✨ Возможности

### 💬 Каналы чата
- **Гибкие каналы** — настраиваются в `config.yml` (`prefix`, `range`, `permission`, `format`, `cooldown`, `public`)
- **Локальный чат** — слышат только игроки в радиусе (по умолчанию 100 блоков)
- **Глобальный чат** — префикс `!` для сообщений всему серверу
- **Staff-канал** — префикс `#` (только для staff, никогда не утекает в Discord)
- **Групповые чаты** — свои приватные группы (`/chat create-group`, `/chat group <name> <msg>`)
- **RP-команды** `/me`, `/do`, `/try`, `/roll` — для ролевой игры
- **Личные сообщения** `/msg`, `/reply` с кликабельными никами
- **Треды** — отвечайте на конкретные сообщения кликом `[↩]`

### 🎨 Оформление
- **MiniMessage** в форматах — цвета, градиенты, hover, click
- **Головы игроков** в чате через SkinsRestorer (`<head:uuid:...>`)
- **Спрайты предметов** и блоков (`<sprite:items:item/diamond>`)
- **Кастомные эмодзи** (`:smile:`, `:heart:`, `:diamond:`)
- **Показ предмета из руки** `[item]` с тултипом (лимит на сообщение)
- **Показ инвентаря** `[inv]` — кликабельный GUI через одноразовый токен (TTL 60 сек)
- **Кликабельные ссылки** — авто-детект URL
- **Кликабельные ники** — `/msg <ник>` по клику в чате
- **Упоминания** `@Ник` — звук и подсветка
- **Префиксы/суффиксы LuckPerms** — с поддержкой `&`-кодов и MiniMessage

### 🛡️ Модерация
- **Cooldown** между сообщениями
- **Анти-дубль** — блок повторов
- **Levenshtein** — блок похожих сообщений
- **Анти-флуд** — блок повторяющихся символов
- **Anti-crash** — блок control-символов и Unicode-бомб
- **Фильтр мата** — замена на `***` или блок при превышении лимита (NFKC, омоглифы)
- **Анти-реклама** — IP, ссылки, домены (с whitelist)
- **Bypass-права** для админов на каждую проверку
- **Асинхронная проверка** — тяжёлые regex/Levenshtein в async-потоке

### 🔧 Инструменты
- **Удаление сообщений** — клик `[✖]` для модераторов (с перерисовкой чата)
- **Соц-шпион** `/socialspy` — админы видят все ЛС
- **Broadcast** `/broadcast` — объявление на весь сервер (+ в Discord)
- **AutoBroadcast** — авто-объявления по расписанию (мультиязычные)
- **История чата** — `/chat history <ник>` с пагинацией
- **Статистика** — `/chat stats [ник]` + PAPI-плейсхолдеры
- **Purge** — `/chat purge <дни>` для очистки старых сообщений
- **Chat Games** — мини-игры в чате (Unscramble, Quiz, Math) с toggle (`/chatgames`)
- **Локальные ответы** — бот отвечает на вопросы из конфига
- **Логирование** — все сообщения в `logs/chat-YYYY-MM-DD.log` (async, ротация, санитизация)

### 🌍 Мультиязычность
- Русский, английский, немецкий из коробки
- Автоопределение языка клиента игрока
- Fallback на английский
- **Overlay**: jar-база + `lang/messages_*.yml` игрока

### 🔌 Интеграции
- **MineSQL** — единый пул соединений для БД (MariaDB/MySQL/H2)
- **PlaceholderAPI** — плейсхолдеры `%terracraftchat_...%` (включая `stats_sent`, `stats_blocked`, `stats_swear`, `stats_ads`)
- **SkinsRestorer** — головы игроков в чате
- **Discord Webhook** — глобальный чат уходит в Discord (с `allowed_mentions`)
- **CommandAPI** — регистрация команд через Paper Brigadier
- **Vault** — (опционально) для будущих фич

---

## 📦 Установка

### Требования

| Компонент | Версия |
|---|---|
| **Paper (или форк)** | 1.21.11+ |
| **Java** | 21+ |
| **CommandAPI** | 12.1.0+ (обязательно) |
| **MineSQL** | 1.4.2+ (обязательно) |
| **PlaceholderAPI** | 2.11.6+ (рекомендуется) |
| **SkinsRestorer** | 15.9.0+ (опционально) |

### Шаги

1. Скачай последний релиз `TerraCraftChat-x.x.x-all.jar` со страницы [Releases](../../releases)
2. Положи JAR в папку `plugins/`
3. Установи [CommandAPI](https://github.com/CommandAPI/CommandAPI/releases) 12.1.0+
4. Установи [MineSQL](https://github.com/CarmJos/MineSQL/releases) 1.4.2+
5. (Опционально) Установи [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) и [SkinsRestorer](https://www.spigotmc.org/resources/skinsrestorer.2124/)
6. Настрой `plugins/MineSQL/config.yml` (секция `terracraft-chat`) для подключения к БД
7. Перезапусти сервер
8. Настрой `plugins/TerraCraftChat/config.yml`
9. Выполни `/chat reload`

### Пример `plugins/MineSQL/config.yml`

```yaml
databases:
  "terracraft-chat":
    driver-type: mariadb
    host: 127.0.0.1
    port: 3306
    database: terra_core
    username: terra
    password: qazwsx123
```

---

## 🎮 Команды

### Игроку

| Команда | Алиасы | Описание | Право |
|---|---|---|---|
| `/msg <ник> <текст>` | `/tell`, `/w`, `/whisper` | Личное сообщение | `terracraftchat.msg` |
| `/reply <текст>` | `/r` | Ответить последнему | `terracraftchat.reply` |
| `/ignore <ник> [время]` | — | Игнорировать (можно на время: `30m`, `1h`, `1d`, `1w`) | `terracraftchat.ignore` |
| `/ignore list` | — | Список игнорируемых | `terracraftchat.ignore` |
| `/channel <id>` | — | Переключить канал | — |
| `/channel reset` | — | Сбросить на дефолтный | — |
| `/nick <ник>` | — | Кастомный ник | `terracraftchat.nick` |
| `/nickreset` | — | Сбросить ник | `terracraftchat.nick` |
| `/me <действие>` | — | RP-действие | — |
| `/do <описание>` | — | RP-описание | — |
| `/try <действие>` | — | RP-попытка | — |
| `/roll [макс]` | — | Бросок кубика | — |

### Группы

| Команда | Описание | Право |
|---|---|---|
| `/chat create-group <имя>` | Создать группу | `terracraftchat.group.create` |
| `/chat invite <группа> <ник>` | Пригласить | `terracraftchat.group.invite` |
| `/chat leave <группа>` | Выйти | `terracraftchat.group.leave` |
| `/chat delete <группа>` | Удалить (владелец) | `terracraftchat.group.delete` |
| `/chat group <группа> [текст]` | Отправить / активировать | `terracraftchat.group.use` |

### Модератору

| Команда | Описание | Право |
|---|---|---|
| `/socialspy` (`/spy`) | Вкл/выкл соц-шпион | `terracraftchat.socialspy` |
| `/chat_delete <uuid>` | Удалить сообщение | `terracraftchat.moderator` |
| `/broadcast <текст>` | Объявление | `terracraftchat.broadcast` |
| `/inv_show player <ник>` | Инвентарь игрока | `terracraftchat.inv.admin` |

### Админу

| Команда | Алиасы | Описание | Право |
|---|---|---|---|
| `/chat reload` | `/tcc reload` | Всё | `terracraftchat.admin` |
| `/chat reload rules` | — | Только `rules.yml` | `terracraftchat.admin` |
| `/chat reload games` | — | Только `games.yml` | `terracraftchat.admin` |
| `/chat reload emojis` | — | Только `emojis.yml` | `terracraftchat.admin` |
| `/chat reload lang` | — | Только локализации | `terracraftchat.admin` |
| `/chat history <ник> [limit] [page]` | — | История с пагинацией | `terracraftchat.admin` |
| `/chat stats [ник]` | — | Статистика | `terracraftchat.stats` |
| `/chat purge <дни>` | — | Очистка старого | `terracraftchat.admin` |
| `/chatgames [on/off/toggle]` | `/cg` | Управление играми | `terracraftchat.admin` |

---

## 🔑 Права

### Основные
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.msg` | `true` | `/msg` |
| `terracraftchat.reply` | `true` | `/reply` |
| `terracraftchat.ignore` | `true` | `/ignore` |
| `terracraftchat.thread` | `true` | Треды |

### Контент
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.emoji` | `true` | `:эмодзи:` |
| `terracraftchat.item` | `true` | `[item]` |
| `terracraftchat.inv` | `true` | `[inv]` |
| `terracraftchat.inv.admin` | OP | `/inv_show player` |
| `terracraftchat.color` | **`false`** | `&`-коды и hex |
| `terracraftchat.nick` | OP | `/nick` |

### Модерация и админ
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.moderator` | OP | Удалять сообщения |
| `terracraftchat.socialspy` | OP | `/socialspy` |
| `terracraftchat.broadcast` | OP | `/broadcast` |
| `terracraftchat.admin` | OP | `/chat reload` |
| `terracraftchat.stats` | OP | `/chat stats` |
| `terracraftchat.channel.staff` | OP | Staff-канал |

### Группы
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.group.create` | `true` | Создание группы |
| `terracraftchat.group.use` | `true` | Писать в группу |
| `terracraftchat.group.invite` | `true` | Приглашать |
| `terracraftchat.group.leave` | `true` | Выходить |
| `terracraftchat.group.delete` | `true` | Удалять |

### Bypass
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.bypass.ads` | OP | Анти-реклама |
| `terracraftchat.bypass.swear` | OP | Мат |
| `terracraftchat.bypass.levenshtein` | OP | Похожесть |
| `terracraftchat.bypass.flood` | OP | Флуд |
| `terracraftchat.bypass.crash` | OP | Anti-crash |
| `terracraftchat.bypass.cooldown` | OP | Cooldown |
| `terracraftchat.bypass.duplicate` | OP | Дубли |

---

## ⚙️ Конфигурация

### `config.yml` — основные настройки

```yaml
formats:        # Форматы (local, global, staff, msg, reply, broadcast)
channels:       # Каналы
threads:        # Треды
display:        # Головы, [item], [inv]
mentions:       # @упоминания
moderation:     # Cooldown, дубли, маты, реклама
rp:             # /me, /do, /try, /roll
socialSpy:      # Соц-шпион
delete:         # Удаление (cacheSize, ttl, clearLines)
logging:        # Логирование
discord:        # Discord webhook
autobroadcast:  # Авто-объявления
emoji:          # Эмодзи
localAnswers:   # Ответы бота
chatGames:      # Мини-игры
storage:        # Префикс таблиц (MineSQL)
```

#### Пример: каналы

```yaml
channels:
  defaultChannel: "local"
  rpRadius: 50
  channels:
    local:
      prefix: ""
      range: 100
      permission: ""
      format: "local"
      cooldown: 0
      publicChannel: true
    global:
      prefix: "!"
      range: -1
      permission: ""
      format: "global"
      cooldown: 0
      publicChannel: true
    staff:
      prefix: "#"
      range: -1
      permission: "terracraftchat.channel.staff"
      format: "staff"
      cooldown: 0
      publicChannel: false   # никогда не утекает в Discord
```

#### Пример: Discord

```yaml
discord:
  enabled: true
  webhookUrl: "https://discord.com/api/webhooks/..."
  username: "TerraCraft Chat"
  format: "**[%world%] %player%:** %message%"
  broadcastPrefix: "**[Объявление]**"
  intervalSeconds: 5
  maxBuffer: 50
```

### `lang/messages_*.yml`

Формат:
```yaml
chat:
  prefix: "<gray>[<gold>TerraCraftChat<gray>] <reset>"
  cooldown: "<red>Подожди <white><seconds> <red>сек."
  thread-active: "<green>Режим ответа включён."

autobroadcast:
  rules: "<yellow>Не забывай читать правила: <white>/rules"
```

### `emojis.yml`

```yaml
smile: "☺"
heart: "<red>❤</red>"
diamond: "<sprite:items:item/diamond>"
```

### `games.yml`

```yaml
games:
  unscramble_easy:
    type: "unscramble"
    weight: 5
    timeout: 30
    words: ["алмаз", "крипер"]
    rewards:
      - "give {player} diamond 1"

  math_easy:
    type: "math"
    weight: 5
    timeout: 15
    operations: ["add", "sub"]
    minNumber: 1
    maxNumber: 50
    rewards:
      - "give {player} iron_ingot 5"
```

---

## 🔌 Использование в чате

| Синтаксис | Что делает |
|---|---|
| `!сообщение` | Глобальный чат |
| `#сообщение` | Staff-чат |
| `@Ник` | Упоминание |
| `[item]` | Предмет из руки |
| `[inv]` | Инвентарь (клик) |
| `:smile:` | Эмодзи |
| `&cКрасный` | Цвет (с правом `terracraftchat.color`) |
| `https://...` | Кликабельная ссылка |

---

## 🎨 PlaceholderAPI

| Плейсхолдер | Описание |
|---|---|
| `%terracraftchat_nick_formatted%` | Ник с MiniMessage |
| `%terracraftchat_nick%` | Ник plain |
| `%terracraftchat_displayname_formatted%` | displayName с тегами |
| `%terracraftchat_displayname%` | displayName plain |
| `%terracraftchat_channel%` | Активный канал |
| `%terracraftchat_channel_prefix%` | Префикс канала |
| `%terracraftchat_ignoring_count%` | Игнор-лист |
| `%terracraftchat_socialspy%` | `true`/`false` |
| `%terracraftchat_thread_active%` | `true`/`false` |
| `%terracraftchat_stats_sent%` | Отправлено сообщений |
| `%terracraftchat_stats_blocked%` | Заблокировано |
| `%terracraftchat_stats_swear%` | Мат поймано |
| `%terracraftchat_stats_ads%` | Реклама поймана |
| `%terracraftchat_channels%` | Кол-во каналов (серверный) |
| `%terracraftchat_threads_enabled%` | `true`/`false` (серверный) |
| `%terracraftchat_games_count%` | Кол-во игр (серверный) |

⚠️ Плейсхолдеры `stats_*` кэшируются на 10 секунд для производительности.

---

## 🔧 Сборка из исходников

### Требования
- JDK 25+
- Gradle 9.1.0+ (или wrapper)

```bash
git clone https://github.com/luci4ka/TerraCraftChat.git
cd TerraCraftChat
./gradlew clean build
```

Готовый JAR будет в `build/libs/`.

---

## 🐛 Известные проблемы

- **SkinsRestorer** — только 15.9.0+
- **Инлайн-тред** — обновляется только на момент отправки родителя
- **Удаление** — через перерисовку (200+ сообщений — возможны «мигания»)
- **MineSQL** — обязателен; без него плагин не запустится
- **Префиксы LuckPerms** — `%luckperms_prefix%` работает только если установлен expansion `Expansion-LuckPerms.jar` в PAPI

---

## 🤝 Вклад

Pull requests приветствуются! Для крупных изменений сначала открой issue.

### Что можно улучшить
- Discord → игра (через бота)
- Дополнительные языки
- Тесты (MockBukkit)
- Больше Chat Games (multiple-choice, reaction)
- PacketEvents-интеграция для удаления без перерисовки

---

## 📄 Лицензия

MIT License.

<div align="center">

**Сделано с ❤️**

</div>
