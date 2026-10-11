# Contributing to TerraCraftChat

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
- Плагин: 1.0
- Paper: 1.21.11-XXX
- Java: 21

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

---

## 🛠 Настройка окружения

### Требования

| Компонент | Версия |
|---|---|
| **JDK** | 21 или 25 |
| **Gradle** | 9.1.0+ (или wrapper) |
| **Git** | любой |

### Шаги

1. **Fork репозитория**

```bash
# Форкни через GitHub UI, потом:
git clone https://github.com/YOUR-USERNAME/TerraCraftChat.git
cd TerraCraftChat
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

Готовый JAR — в `build/libs/Chat-1.0.jar`.

4. **Установи на тестовый сервер**

Скачай Paper 1.21.11 и положи в `run/paper.jar`, затем:

```bash
./gradlew runServer
```

Или вручную: скопируй JAR в `plugins/` твоего тестового сервера.

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
- [ ] JAR собирается в `build/libs/Chat-1.0.jar`
- [ ] Протестировано на Paper 1.21.11
- [ ] Нет хардкода языка (всё в `messages_*.yml`)
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
2. Запустил на Paper 1.21.11
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
TerraCraftChat/
├── src/main/
│   ├── java/ru/net/terracraft/chat/
│   │   ├── TerraCraftChatPlugin.java    # Точка входа
│   │   ├── ChatListener.java            # AsyncChatEvent
│   │   ├── ChatFormatter.java           # Форматирование сообщений
│   │   ├── ChatModerator.java           # Модерация
│   │   ├── ChatConfig.java              # Конфиг
│   │   ├── command/                     # Команды
│   │   ├── config/                      # Секции конфига
│   │   └── game/                        # Chat Games
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
| `TerraCraftChatPlugin` | Инициализация, регистрация |
| `ChatListener` | Перехват событий, роутинг |
| `ChatFormatter` | Сборка Component из формата + данных |
| `ChatModerator` | Проверки (cooldown, маты, реклама) |
| `ThreadManager` | Треды (ответы на сообщения) |
| `ChatGameManager` | Мини-игры |

---

## ❓ Вопросы

Если что-то непонятно — создай issue с меткой `question`. Мы поможем.

**Спасибо за вклад! ❤️**