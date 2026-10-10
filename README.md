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
- **Локальный чат** — слышат только игроки в радиусе (по умолчанию 100 блоков)
- **Глобальный чат** — префикс `!` для сообщений всему серверу
- **RP-команды** `/me`, `/do`, `/try`, `/roll` — для ролевой игры
- **Личные сообщения** `/msg`, `/reply` с кликабельными никами
- **Треды** — отвечайте на конкретные сообщения кликом `[↩]`

### 🎨 Оформление
- **MiniMessage** в форматах — цвета, градиенты, hover, click
- **Головы игроков** в чате через SkinsRestorer (`<head:...>`)
- **Спрайты предметов** и блоков (`<sprite:items:item/diamond>`)
- **Кастомные эмодзи** (`:smile:`, `:heart:`, `:diamond:`)
- **Показ предмета из руки** `[item]` с тултипом
- **Показ инвентаря** `[inv]` — кликабельный GUI (только чужой)
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
- **Анти-реклама** — IP, ссылки, домены (с whitelist)
- **Bypass-права** для админов на каждую проверку

### 🔧 Инструменты
- **Удаление сообщений** — клик `[✖]` для модераторов
- **Соц-шпион** `/socialspy` — админы видят все ЛС
- **Broadcast** `/broadcast` — объявление на весь сервер
- **AutoBroadcast** — авто-объявления по расписанию (мультиязычные)
- **Chat Games** — мини-игры в чате (Unscramble слова)
- **Локальные ответы** — бот отвечает на вопросы из конфига
- **Логирование** — все сообщения в `logs/chat-YYYY-MM-DD.log`

### 🌍 Мультиязычность
- Русский, английский, немецкий из коробки
- Автоопределение языка клиента игрока
- Fallback на английский, если перевода нет
- Легко добавить новый язык — просто новый `messages_xx.yml`

### 🔌 Интеграции
- **PlaceholderAPI** — все плейсхолдеры в форматах
- **SkinsRestorer** — головы игроков в чате
- **Discord Webhook** — глобальный чат уходит в Discord
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
| `/me <действие>` | — | RP-действие от первого лица | — |
| `/do <описание>` | — | RP-описание от третьего лица | — |
| `/try <действие>` | — | RP-попытка (успех/провал) | — |
| `/roll [макс]` | — | Бросок кубика | — |

### Модератору

| Команда | Описание | Право |
|---|---|---|
| `/socialspy` (`/spy`) | Вкл/выкл режим соц-шпиона | `terracraftchat.socialspy` |
| `/chat_delete <uuid>` | Удалить сообщение (клик `[✖]`) | `terracraftchat.moderator` |
| `/broadcast <текст>` (`/bc`, `/announce`) | Объявление на весь сервер | `terracraftchat.broadcast` |

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
| `terracraftchat.color` | **`false`** | Использовать `&`-коды и hex (для донатеров) |

### Модерация и админ
| Право | По умолчанию | Описание |
|---|---|---|
| `terracraftchat.moderator` | OP | Удалять сообщения кликом `[✖]` |
| `terracraftchat.socialspy` | OP | Видеть все ЛС через `/socialspy` |
| `terracraftchat.broadcast` | OP | Использовать `/broadcast` |
| `terracraftchat.admin` | OP | `/chat reload` |

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
```

---

## ⚙️ Конфигурация

### `config.yml` — основные настройки

Конфиг разбит на **секции**:

```yaml
formats:        # Форматы сообщений
channels:       # Каналы (local/global/rp)
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

### `lang/messages_*.yml` — переводы

- `messages_ru.yml` — русский
- `messages_en.yml` — английский
- `messages_de.yml` — немецкий

Формат:
```yaml
chat:
  prefix: "<gray>[<gold>TerraCraftChat<gray>] <reset>"
  cooldown: "<red>Подожди <white><seconds> <red>сек."
  thread-active: "<green>Режим ответа включён. Следующее сообщение уйдёт в тред."

autobroadcast:
  rules: "<yellow>Не забывай читать правила: <white>/rules"
```

### `emojis.yml` — эмодзи

```yaml
smile: "☺"
heart: "<red>❤</red>"
diamond: "<sprite:items:item/diamond>"
star: "<yellow>★</yellow>"
```

---

## 🔌 Использование в чате

| Синтаксис | Что делает |
|---|---|
| `!сообщение` | Глобальный чат (видит весь сервер) |
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

Готовый JAR будет в `build/libs/Chat-x.x.jar`.


---

## 🐛 Известные проблемы

- **SkinsRestorer** работает только с версии 15.9.0+ (для голов в чате)
- **Инлайн-тред** обновляется только на момент отправки родителя — при ответе через 5 минут инлайн-панель не перерисуется (ограничение клиента Minecraft)

---

## 🤝 Вклад

Pull requests приветствуются! Для крупных изменений сначала открой issue с описанием.

### Что можно улучшить
- Discord → игра (через бота, а не webhook)
- Дополнительные языки
- Тесты (MockBukkit)
- Больше Chat Games (математика, викторины)

---

## 📄 Лицензия

MIT License — используй, изменяй, распространяй свободно.

Полный текст: [LICENSE](LICENSE)

---

<div align="center">

**Сделано с ❤️ для TerraCraft**

</div>