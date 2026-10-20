Спасибо за интерес к проекту! Мы рады любому вкладу — от исправления опечаток до новых фич.

## 📋 Содержание

- [Кодекс поведения](#-кодекс-поведения)
- [Как помочь](#-как-помочь)
- [Настройка окружения](#-настройка-окружения)
- [Стиль кода](#-стиль-кода)
- [Процесс PR](#-процесс-pr)
- [Структура проекта](#-структура-проекта)

---

## 🤝 Кодекс поведения

- Будь вежлив и уважителен
- Конструктивная критика приветствуется
- Спам и оскорбления — бан
- Помогай новичкам, если можешь

---

## 💡 Как помочь

### 🐛 Баг-репорты

Перед созданием issue:
1. Проверь, что проблема **воспроизводится на последней версии**
2. Поищи в [существующих issues](../../issues) — возможно, уже обсуждалось
3. Собери **максимум информации**: версия плагина, версия Paper, лог, шаги воспроизведения

**Шаблон баг-репорта:**
```markdown
**Описание:** Что происходит?

**Ожидаемое поведение:** Что должно быть?

**Шаги воспроизведения:**
1. ...
2. ...

**Версии:**
- Плагин: 1.0.0
- Paper: 1.21.11-XXX
- Java: 21
- CommandAPI: 12.1.0
- PlaceholderAPI: 2.12.3 (опционально)

**Лог:** (вставить ошибку из latest.log)

**Конфиг:** (если проблема в конфиге)
```

### ✨ Фичи

Перед PR с новой фичей — **создай issue** и опиши:
- Что за фича
- Зачем она нужна
- Как ты её видишь

Это поможет избежать ситуации «сделал PR, а он не нужен».

### 📝 Pull Request

Мы рады:
- Исправлению багов
- Улучшению документации
- Рефакторингу
- Новым фичам (после обсуждения в issue)
- Дополнительным языкам
- Тестам
- Новым Chat Games (`games.yml`)

---

## 🛠 Настройка окружения

### Требования

| Компонент | Версия |
|---|---|
| **JDK** | 21+ (Paper 1.21.11 требует 21) |
| **Gradle** | 9.1.0+ (или wrapper) |
| **Git** | любой |

### Шаги

1. **Fork репозитория**

```bash
# Форкни через GitHub UI, потом:
git clone https://github.com/YOUR-USERNAME/Chat.git
cd Chat
```

2. **Настрой Git (Windows)**

```bash
git config core.autocrlf false
git config core.eol lf
```

Это **обязательно** для Windows — иначе Git будет считать все файлы изменёнными из-за CRLF/LF.

3. **Собери проект**

```bash
./gradlew clean build
```

Готовый JAR — в `build/libs/Chat-1.0.0.jar`.

4. **Установи на тестовый сервер**

Скачай Paper 1.21.11 и положи в `run/paper.jar`, затем:

```bash
./gradlew runServer
```

Или вручную: скопируй JAR в `plugins/` твоего тестового сервера. **Не забудь** установить:
- [CommandAPI](https://github.com/CommandAPI/CommandAPI/releases) 12.1.0+
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (опционально)
- [SkinsRestorer](https://www.spigotmc.org/resources/skinsrestorer.2124/) (опционально, для голов)

---

## 🎨 Стиль кода

### Java

- **Отступы**: 4 пробела
- **Фигурные скобки**: на той же строке (`K&R`)
- **Длина строки**: до 120 символов
- **Именование**:
  - Классы: `PascalCase`
  - Методы/поля: `camelCase`
  - Константы: `UPPER_SNAKE_CASE`

**Пример:**
```java
public class ChatFormatter {

    private static final int MAX_LENGTH = 100;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component formatMessage(Player sender, String message) {
        if (message.isEmpty()) {
            return Component.empty();
        }
        return miniMessage.deserialize(message);
    }
}
```

### Комментарии

- **Javadoc** для публичных методов
- **Инлайн-комментарии** только для неочевидных мест
- Не комментируй очевидное (`// increment i`)

### Импорты

- Без wildcard (`import java.util.*` — **плохо**)
- Отсортированы: сначала Java, потом библиотеки, потом Minecraft
- Удаляй неиспользуемые импорты

### Логика

- **Никаких хардкоженных строк** — только i18n (`messages_*.yml`) или конфиг
- **Никакого blocking I/O в main thread** — используй `runTaskAsynchronously`
- **Всегда отменяй задачи** в `onDisable` (scheduler, HTTP, etc.)
- **`Locale.ROOT`** в `toLowerCase()`/`toUpperCase()` — иначе турецкая локаль ломает
- **Потокобезопасность**: `ConcurrentHashMap`, `volatile`, `synchronized` где нужно
- **`Player` из async-потока** — нельзя. Используй снапшоты (UUID, имя) или `runTask`

### Работа с `Player` и `AsyncChatEvent`

`AsyncChatEvent` вызывается **асинхронно**. Правила:
- Читать `sender.hasPermission(...)`, `sender.getName()` — **можно**
- Вызывать `Bukkit.getOnlinePlayers()`, `player.sendMessage(...)` — **нельзя** (только через `runTask`)
- Тяжёлые проверки (`ChatModerator.check`, Levenshtein, regex) — **в async**
- Всё, что трогает `Player`/`Bukkit` — **в main** (`runTask`)

**Паттерн:** async-проверка → `runTask` → рассылка.

---

## 🚀 Процесс PR

### 1. Создай ветку

```bash
git checkout -b feature/my-feature
# или
git checkout -b fix/my-bugfix
```

**Именование веток:**
- `feature/xxx` — новая фича
- `fix/xxx` — багфикс
- `docs/xxx` — документация
- `refactor/xxx` — рефакторинг

### 2. Делай коммиты

**Формат:**
```
<тип>: <короткое описание>

<опциональное подробное описание>

<опциональные ссылки на issues>
```

**Типы:**
- `feat:` — новая фича
- `fix:` — багфикс
- `docs:` — документация
- `refactor:` — рефакторинг
- `test:` — тесты
- `chore:` — рутина (обновление зависимостей и т.п.)

**Примеры:**
```
feat: add trade chat command
fix: correct cooldown timer in ChatModerator
docs: update README with new config sections
refactor: extract ChatFormatter into separate methods
```

### 3. Проверь перед PR

```bash
# Сборка
./gradlew clean build

# Проверь, что JAR создаётся
ls build/libs/

# Проверь на тестовом сервере
./gradlew runServer
```

**Чек-лист:**
- [ ] Код компилируется без ошибок
- [ ] Нет warnings от компилятора (или минимум)
- [ ] JAR собирается в `build/libs/Chat-1.0.0.jar`
- [ ] Протестировано на Paper 1.21.11 + CommandAPI 12.1.0
- [ ] Нет хардкода языка (всё в `messages_*.yml`)
- [ ] Нет `Player`-вызовов из async-потока
- [ ] `Locale.ROOT` в `toLowerCase`/`toUpperCase`
- [ ] Обновлён README, если менялось API/команды/конфиг
- [ ] Обновлён `CHANGELOG.md` (если есть)

### 4. Открой PR

**Заголовок PR:**
```
feat: добавлен /trade
```

**Описание PR:**
```markdown
## Что сделано
- Добавлена команда /trade
- Добавлена секция trade в config.yml
- Обновлён README

## Связанные issues
Closes #123

## Как тестировал
1. Собрал JAR
2. Запустил на Paper 1.21.11 + CommandAPI 12.1.0
3. Проверил /trade, /tc, /tr

## Скриншоты
(если применимо)
```

### 5. Дождись ревью

- Мейнтейнер может попросить изменения
- Вноси изменения **в ту же ветку** — PR обновится автоматически
- После одобрения — merge

---

## 📁 Структура проекта

```
Chat/
├── src/main/
│   ├── java/ru/net/terracraft/chat/
│   │   ├── TerraCraftChatPlugin.java    # Точка входа
│   │   ├── command/                     # Команды (/msg, /nick, /channel, /inv_show, ...)
│   │   ├── config/                      # ChatConfig (все секции конфига)
│   │   ├── format/                      # ChatFormatter, NickFormat, HeadDisplayManager,
│   │   │                                # TranslationManager
│   │   ├── game/                        # ChatGameManager, GamesConfig, GameDefinition,
│   │   │                                # ShuffleBag
│   │   ├── integration/                 # AutoBroadcast, DiscordWebhook, EmojiManager,
│   │   │                                # LocalAnswerManager, UpdateNotifier, ChatPlaceholders
│   │   ├── listener/                    # ChatListener, PlayerDataListener, InvListener
│   │   ├── manager/                     # ChannelManager, ChatMessageRegistry, ChatRedrawManager,
│   │   │                                # IgnoreManager, InvTokenManager, NickManager,
│   │   │                                # ReplyManager, SkinHeadCache, SocialSpyManager,
│   │   │                                # ThreadManager
│   │   ├── model/                       # Channel, ChatMessage, InvViewHolder
│   │   ├── moderation/                  # ChatLogger, ChatModerator
│   │   └── util/                        # LevenshteinUtil, MessageUtil
│   └── resources/
│       ├── paper-plugin.yml
│       ├── games.yml
│       ├── emojis.yml
│       └── lang/
│           ├── messages_ru.yml
│           ├── messages_en.yml
│           └── messages_de.yml
├── build.gradle
├── settings.gradle
├── CONTRIBUTING.md
├── README.md
└── LICENSE
```

### Ключевые классы

| Класс | Ответственность |
|---|---|
| `TerraCraftChatPlugin` | Инициализация, регистрация, `reload()` |
| `ChatListener` | `AsyncChatEvent`, роутинг (модерация → игра → тред → чат) |
| `ChatFormatter` | Сборка `Component` из формата + данных |
| `ChatModerator` | Проверки: cooldown, дубли, Levenshtein, маты, реклама, anti-crash |
| `ChannelManager` | Каналы (local/global/staff + свои), `resolve()` по префиксу |
| `ThreadManager` | Треды (ответы на сообщения) |
| `ChatGameManager` | Мини-игры (Unscramble, Quiz, Math) |
| `ChatRedrawManager` | Перерисовка чата при удалении (работает для онлайн + офлайн) |
| `InvTokenManager` | Одноразовые токены для `[inv]` |
| `NickFormat` | Безопасный `MiniMessage` для ников (только цвета) |
| `ChatPlaceholders` | PAPI-плейсхолдеры `%terracraftchat_...%` |
| `TranslationManager` | Загрузка локалей, overlay из jar + `lang/` |

### Поток сообщения

1. **`AsyncChatEvent`** (async) — `ChatListener.onChat`
2. `ChannelManager.resolve` — определяем канал (префикс → активный → default)
3. `ChatModerator.check` — проверки (async-safe)
4. `record()` — кэш cooldown (async-safe)
5. **`runTask`** (main) — блокирующие случаи (cooldown, мат, реклама)
6. `ChatGameManager.checkAnswer` — если активна игра
7. `ThreadManager.postReply` — если активен тред
8. `ChatListener.handleChannel` — рассылка зрителям + side effects (Discord, бот, консоль, пузырь)

---

## ❓ Вопросы

Если что-то непонятно — создай issue с меткой `question`. Мы поможем.

**Спасибо за вклад! ❤️**