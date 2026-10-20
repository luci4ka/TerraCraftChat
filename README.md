<div align="center">

# TerraCraftChat

**Современный чат-плагин для Paper 1.21.11**

[![Paper](https://img.shields.io/badge/Paper-1.21.11-blue?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21+-orange?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

Локальный и глобальный чат, ЛС, треды, модерация, эмодзи, головы игроков, Discord-мост и многое другое.

</div>

---

## ✨ Возможности

### 💬 Каналы чата
- **Гибкие каналы** — настраиваются в `config.yml` (`prefix`, `range`, `permission`, `format`, `cooldown`, `public`)
- **Локальный чат** — слышат только игроки в радиусе (по умолчанию 100 блоков)
- **Глобальный чат** — префикс `!` для сообщений всему серверу
- **Staff-канал** — префикс `#` (только для staff, никогда не утекает в Discord)
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

### 🛡️ Модерация
- **Cooldown** между сообщениями
- **Анти-дубль** — блок повторов
- **Levenshtein** — блок похожих сообщений (например, изменённых на 1 букву)
- **Анти-флуд** — блок повторяющихся символов (`ааааааа`)
- **Anti-crash** — блок control-символов и Unicode-бомб
- **Фильтр мата** — замена на `***` или блок при превышении лимита
  - NFKC-нормализация, замена латинских омоглифов, удаление пробелов между буквами
- **Анти-реклама** — IP, ссылки, домены (с whitelist)
  - Нормализация `evil . com` → `evil.com`, защита от `terracraft.net.ru evil.com`
- **Bypass-права** для админов на каждую проверку
- **Асинхронная проверка** — тяжёлые regex/Levenshtein в async-потоке

### 🔧 Инструменты
- **Удаление сообщений** — клик `[✖]` для модераторов
  - Перерисовка чата через `ChatRedrawManager` (работает для онлайн + офлайн)
  - Очистка + повторная отправка истории без удалённого
- **Соц-шпион** `/socialspy` — админы видят все ЛС
- **Broadcast** `/broadcast` — объявление на весь сервер (+ в Discord)
- **AutoBroadcast** — авто-объявления по расписанию (мультиязычные)
- **Chat Games** — мини-игры в чате (Unscramble, Quiz, Math)
  - `ShuffleBag` — слова/вопросы не повторяются, пока не кончатся
  - Per-game `timeout`, `weight`, `rewards`
  - Cooldown на неправильные ответы (3 сек)
- **Локальные ответы** — бот отвечает на вопросы из конфига
- **Логирование** — все сообщения в `logs/chat-YYYY-MM-DD.log`
  - Асинхронная запись, ротация по датам, санитизация `\n`/`\r`

### 🌍 Мультиязычность
- Русский, английский, немецкий из коробки
- Автоопределение языка клиента игрока
- Fallback на английский, если перевода нет
- **Overlay**: jar-база + `lang/messages_*.yml` игрока (новые ключи всегда есть)
- Легко добавить новый язык — просто новый `messages_xx.yml`

### 🔌 Интеграции
- **PlaceholderAPI** — плейсхолдеры `%terracraftchat_...%`
  - `%terracraftchat_nick_formatted%` — ник с MiniMessage-тегами (для таба)
  - `%terracraftchat_nick%` — plain text
  - `%terracraftchat_channel%`, `%terracraftchat_channel_prefix%`
  - `%terracraftchat_ignoring_count%`, `%terracraftchat_socialspy%`
  - `%terracraftchat_thread_active%`, `%terracraftchat_channels%`
  - `%terracraftchat_threads_enabled%`, `%terracraftchat_games_count%`
- **SkinsRestorer** — головы игроков в чате
- **Discord Webhook** — глобальный чат уходит в Discord (с `allowed_mentions` против `@everyone`)
- **CommandAPI** — регистрация команд через Paper Brigadier

---

## 📦 Установка

### Требования

| Компонент | Версия |
|---|---|
| **Paper (или форк)** | 1.21.11+ |
| **Java** | 21+ |
| **CommandAPI** | 12.1.0+ (обязательно) |
| **PlaceholderAPI** | 2.11.6+ (рекомендуется) |
| **SkinsRestorer** | 15.9.0+ (опционально, для голов) |

### Шаги

1. Скачай последний релиз `TerraCraftChat-x.x.x-all.jar` со страницы [Releases](../../releases)
2. Положи JAR в папку `plugins/` твоего сервера
3. Установи [CommandAPI](https://github.com/CommandAPI/CommandAPI/releases) 12.1.0+ в `plugins/`
4. (Опционально) Установи [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) и [SkinsRestorer](https://www.spigotmc.org/resources/skinsrestorer.2124/)
5. Перезапусти сервер
6. Настрой `plugins/TerraCraftChat/config.yml` под свой сервер
7. Выполни `/chat reload`

---

## 🎮 Команды

### Игроку

| Команда | Алиасы | Описание | Право |
|---|---|---|---|
| `/msg <ник> <текст>` | `/tell`, `/w`, `/whisper` | Личное сообщение | `terracraftchat.msg` |
| `/reply <текст>` | `/r` | Ответить последнему собеседнику | `terracraftchat.reply` |
| `/ignore <ник>` | — | Игнорировать игрока (тоггл) | `terracraftchat.ignore` |
| `/ignore list` | — | Список игнорируемых | `terracraftchat.ignore` |
| `/channel <id>` | — | Переключить активный канал | — |
| `/channel reset` | — | Сбросить на канал по умолчанию | — |
| `/nick <ник>` | — | Установить кастомный ник | `terracraftchat.nick` |
| `/nickreset` | — | Сбросить ник | `terracraftchat.nick` |
| `/me <действие>` | — | RP-действие от первого лица | — |
| `/do <описание>` | — | RP-описание от третьего лица | — |
| `/try <действие>` | — | RP-попытка (успех/провал) | — |
| `/roll [макс]` | — | Бросок кубика | — |

### Модератору

| Команда | Описание | Право |
|---|---|---|
| `/socialspy` (`/spy`) | Вкл/выкл режим соц-шпиона | `terracraftchat.socialspy` |
| `/chat_delete <uuid>` | Удалить сообщение (клик `[✖]`) | `terracraftchat.moderator` |
| `/broadcast <текст>` (`/bc`, `/announce`, `/tcbroadcast`) | Объявление на весь сервер | `terracraftchat.broadcast` |
| `/inv_show player <ник>` | Просмотр инвентаря игрока | `terracraftchat.inv.admin` |

### Админу

| Команда | Алиасы | Описание | Право |
|---|---|---|---|
| `/chat reload` | `/tcc reload` | Перезагрузить конфиг | `terracraftchat.admin` |

---

## 🔑 Права

### Основные
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.msg` | `true` | Доступ к `/msg` |
| `terracraftchat.reply` | `true` | Доступ к `/reply` |
| `terracraftchat.ignore` | `true` | Доступ к `/ignore` |
| `terracraftchat.thread` | `true` | Отвечать в треды через `[↩]` |

### Контент
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.emoji` | `true` | Использовать `:эмодзи:` |
| `terracraftchat.item` | `true` | Использовать `[item]` |
| `terracraftchat.inv` | `true` | Использовать `[inv]` |
| `terracraftchat.inv.admin` | OP | `/inv_show player <ник>` |
| `terracraftchat.color` | **`false`** | Использовать `&`-коды и hex (для донатеров) |
| `terracraftchat.nick` | OP | Устанавливать кастомный ник |

### Модерация и админ
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.moderator` | OP | Удалять сообщения кликом `[✖]` |
| `terracraftchat.socialspy` | OP | Видеть все ЛС через `/socialspy` |
| `terracraftchat.broadcast` | OP | Использовать `/broadcast` |
| `terracraftchat.admin` | OP | `/chat reload` |
| `terracraftchat.channel.staff` | OP | Доступ к staff-каналу (`#`) |

### Bypass (обход проверок)
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.bypass.ads` | OP | Обход анти-рекламы |
| `terracraftchat.bypass.swear` | OP | Обход фильтра мата |
| `terracraftchat.bypass.levenshtein` | OP | Обход похожести |
| `terracraftchat.bypass.flood` | OP | Обход анти-флуда |
| `terracraftchat.bypass.crash` | OP | Обход anti-crash |
| `terracraftchat.bypass.cooldown` | OP | Обход cooldown |
| `terracraftchat.bypass.duplicate` | OP | Обход анти-дубля |

### Пример выдачи прав через LuckPerms

```bash
# Донатерам — цвета
/lp group vip permission set terracraftchat.color true

# Модераторам — инструменты
/lp group mod permission set terracraftchat.moderator true
/lp group mod permission set terracraftchat.socialspy true
/lp group mod permission set terracraftchat.broadcast true
/lp group mod permission set terracraftchat.bypass.cooldown true

# Staff-канал
/lp group mod permission set terracraftchat.channel.staff true
```

---

## ⚙️ Конфигурация

### `config.yml` — основные настройки

Конфиг разбит на **секции**:

```yaml
formats:        # Форматы сообщений (local, global, staff, msg, reply, broadcast)
channels:       # Каналы (local/global/staff + любые свои)
threads:        # Треды (ответы на сообщения)
display:        # Головы, [item], [inv], сообщения над головой
mentions:       # @упоминания
moderation:     # Cooldown, дубли, маты, реклама
rp:             # /me, /do, /try, /roll
socialSpy:      # Соц-шпион
delete:         # Удаление сообщений
logging:        # Логирование
discord:        # Discord webhook
autobroadcast:  # Авто-объявления
emoji:          # Эмодзи
localAnswers:   # Локальные ответы бота
chatGames:      # Мини-игры
```

#### Пример: каналы

```yaml
channels:
  default-channel: "local"
  rp-radius: 50
  channels:
    local:
      prefix: ""
      range: 100
      permission: ""
      format: "local"
      cooldown: 0
      public-channel: true
    global:
      prefix: "!"
      range: -1
      permission: ""
      format: "global"
      cooldown: 0
      public-channel: true
    staff:
      prefix: "#"
      range: -1
      permission: "terracraftchat.channel.staff"
      format: "staff"
      cooldown: 0
      public-channel: false   # никогда не утекает в Discord
```

#### Пример: Discord

```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."
  username: "TerraCraft Chat"
  format: "**[%world%] %player%:** %message%"
  broadcast-prefix: "**[Объявление]**"
  interval-seconds: 5
  max-buffer: 50
```

### `lang/messages_*.yml` — переводы

- `messages_ru.yml` — русский
- `messages_en.yml` — английский
- `messages_de.yml` — немецкий

Формат:
```yaml
chat:
  prefix: "<gray>[<gold>TerraCraftChat<gray>] <reset>"
  cooldown: "<red>Подожди <white><seconds> <red>сек."
  thread-active: "<green>Режим ответа включён."

autobroadcast:
  rules: "<yellow>Не забывай читать правила: <white>/rules"
```

**Overlay**: jar-база + `lang/` игрока. Если обновился jar, новые ключи подхватятся, а ваши переопределения — сохранятся.

### `emojis.yml` — эмодзи

```yaml
smile: "☺"
heart: "<red>❤</red>"
diamond: "<sprite:items:item/diamond>"
star: "<yellow>★</yellow>"
```

### `games.yml` — Chat Games

```yaml
games:
  unscramble_easy:
    type: "unscramble"
    weight: 5
    timeout: 30
    words:
      - "алмаз"
      - "крипер"
    rewards:
      - "give {player} diamond 1"

  quiz_minecraft:
    type: "quiz"
    weight: 3
    timeout: 45
    questions:
      - question: "Какой моб взрывается?"
        answers: ["крипер", "creeper"]
    rewards:
      - "give {player} emerald 3"

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
| `!сообщение` | Глобальный чат (видит весь сервер) |
| `#сообщение` | Staff-чат (только с правом) |
| `@Ник` | Упоминание игрока (звук + подсветка) |
| `[item]` | Показать предмет из руки |
| `[inv]` | Кликабельный `[Инвентарь]` (только чужой) |
| `:smile:` | Кастомный эмодзи |
| `&cКрасный` | Цвет (только с правом `terracraftchat.color`) |
| `https://...` | Кликабельная ссылка |

### 🧵 Треды

Под каждым сообщением есть кнопка `[↩]`. Нажмите — и следующее ваше сообщение уйдёт **в тред** к этому сообщению. Все, кто видел родителя, увидят ответ.

```
[L] Steve: привет всем
  [↩]
  ↳ Alex: тоже привет
  ↳ Maria: как дела?
  ... ещё 2 ответов
```

- **Инлайн-тред** — до 3 последних ответов под родителем
- **Один ответ за раз** — после ответа режим сбрасывается
- **Лимит** — настраивается в `config.yml` → `threads.max-thread-replies`
- **Запрет на своё сообщение** — нельзя отвечать на свой же родитель

---

## 🎨 PlaceholderAPI

Плейсхолдеры доступны, если установлен PAPI:

| Плейсхолдер | Описание |
|---|---|
| `%terracraftchat_nick_formatted%` | Ник с MiniMessage-тегами (для таба) |
| `%terracraftchat_nick%` | Ник без тегов |
| `%terracraftchat_displayname_formatted%` | displayName с тегами |
| `%terracraftchat_displayname%` | displayName plain |
| `%terracraftchat_channel%` | Активный канал |
| `%terracraftchat_channel_prefix%` | Префикс канала |
| `%terracraftchat_ignoring_count%` | Сколько в игнор-листе |
| `%terracraftchat_socialspy%` | `true`/`false` |
| `%terracraftchat_thread_active%` | `true`/`false` |
| `%terracraftchat_channels%` | Кол-во каналов (серверный) |
| `%terracraftchat_threads_enabled%` | `true`/`false` (серверный) |
| `%terracraftchat_games_count%` | Кол-во игр (серверный) |

---

## 🔧 Сборка из исходников

### Требования
- JDK 21+
- Gradle 9.1.0+ (или используй wrapper)

### Сборка

```bash
git clone https://github.com/luci4ka/TerraCraftChat.git
cd TerraCraftChat
./gradlew clean build
```

Готовый JAR будет в `build/libs/Chat-x.x.x.jar`.

---

## 🐛 Известные проблемы

- **SkinsRestorer** работает только с версии 15.9.0+ (для голов в чате)
- **Инлайн-тред** обновляется только на момент отправки родителя — при ответе через 5 минут инлайн-панель не перерисуется (ограничение клиента Minecraft)
- **Удаление** работает через перерисовку чата (очистка + повторная отправка истории). Для 200+ сообщений возможны «мигания»
- **Офлайн-игроки** (без подписи) не получают «настоящее» удаление через `SignedMessage` — только перерисовку

---

## 🤝 Вклад

Pull requests приветствуются! Для крупных изменений сначала открой issue с описанием.

### Что можно улучшить
- Discord → игра (через бота, а не webhook)
- Дополнительные языки
- Тесты (MockBukkit)
- Больше Chat Games (multiple-choice, reaction)
- PacketEvents-интеграция для удаления без перерисовки

---

## 📄 Лицензия

MIT License — используй, изменяй, распространяй свободно.

Полный текст: [LICENSE](LICENSE)

---

<div align="center">

**Сделано с ❤️**

</div>
