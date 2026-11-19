package ru.terracraft.chat.moderation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.terracraft.chat.config.ChatConfig;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("ChatModerator — модерация сообщений")
class ChatModeratorTest {

    private static final ChatModerator.Bypass NO_BYPASS =
            new ChatModerator.Bypass(false, false, false, false, false, false, false);

    /** Конфиг с отключённым кулдауном — чтобы тесты дублей/похожести не упирались в него. */
    private static ChatConfig withoutCooldown() {
        return config("""
                moderation:
                  cooldown: 0
                """);
    }

    private static ChatConfig config(String yaml) {
        return de.exlll.configlib.YamlConfigurations.read(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                ChatConfig.class
        );
    }

    private static ChatModerator moderator() {
        return new ChatModerator(withoutCooldown());
    }

    private static ChatModerator.Bypass bypassAll() {
        return new ChatModerator.Bypass(true, true, true, true, true, true, true);
    }

    private static String allowed(ChatModerator.Outcome outcome) {
        return assertInstanceOf(ChatModerator.Outcome.Allowed.class, outcome).message();
    }

    private static String censored(ChatModerator.Outcome outcome) {
        return assertInstanceOf(ChatModerator.Outcome.Censored.class, outcome).message();
    }

    private static ChatModerator.Result blocked(ChatModerator.Outcome outcome) {
        return assertInstanceOf(ChatModerator.Outcome.Blocked.class, outcome).reason();
    }

    @Nested
    @DisplayName("Анти-реклама")
    class Advertisement {

        @Test
        void blocksIpAddress() {
            assertEquals(ChatModerator.Result.ADVERTISEMENT,
                    blocked(moderator().check(UUID.randomUUID(), "зайди на 192.168.1.55", NO_BYPASS)));
        }

        @Test
        void blocksHttpLink() {
            assertEquals(ChatModerator.Result.ADVERTISEMENT,
                    blocked(moderator().check(UUID.randomUUID(), "смотри http://spam.example", NO_BYPASS)));
        }

        @Test
        void blocksBareDomain() {
            assertEquals(ChatModerator.Result.ADVERTISEMENT,
                    blocked(moderator().check(UUID.randomUUID(), "заходи на наш site.xyz", NO_BYPASS)));
        }

        @Test
        @DisplayName("домен из allowedDomains не считается рекламой")
        void allowsWhitelistedDomain() {
            String message = "наш сайт terracraft.net.ru — заходи";
            assertEquals(message, allowed(moderator().check(UUID.randomUUID(), message, NO_BYPASS)));
        }

        @Test
        @DisplayName("whitelist не спасает, если в сообщении есть ещё один домен")
        void whitelistDoesNotHideSecondDomain() {
            assertEquals(ChatModerator.Result.ADVERTISEMENT, blocked(moderator().check(
                    UUID.randomUUID(), "terracraft.net.ru и site.xyz", NO_BYPASS)));
        }

        @Test
        void bypassPermissionSkipsAdsCheck() {
            assertEquals("зайди на 192.168.1.55",
                    allowed(moderator().check(UUID.randomUUID(), "зайди на 192.168.1.55", bypassAll())));
        }
    }

    @Nested
    @DisplayName("мат")
    class Swearing {

        @Test
        @DisplayName("один мат цензурируется, сообщение доходит")
        void censorsSingleSwear() {
            assertEquals("ты ***", censored(
                    moderator().check(UUID.randomUUID(), "ты сука", NO_BYPASS)));
        }

        @Test
        @DisplayName("мат в другом регистре тоже находится")
        void censorsUppercaseSwear() {
            assertEquals("***", censored(moderator().check(UUID.randomUUID(), "СУКА", NO_BYPASS)));
        }

        @Test
        @DisplayName("гомоглифы (латиница вместо кириллицы) распознаются")
        void censorsHomoglyphSwear() {
            assertEquals("***", censored(moderator().check(UUID.randomUUID(), "cука", NO_BYPASS)));
        }

        @Test
        @DisplayName("буквы, разделённые пробелами/точками, склеиваются")
        void censorsSpacedSwear() {
            assertEquals("***", censored(moderator().check(UUID.randomUUID(), "с у к а", NO_BYPASS)));
        }

        @Test
        @DisplayName("если мата больше maxSwearWords — блок")
        void blocksTooManySwears() {
            assertEquals(ChatModerator.Result.SWEAR_BLOCK, blocked(moderator().check(
                    UUID.randomUUID(), "сука хуй блядь", NO_BYPASS)));
        }

        @Test
        @DisplayName("часть слова не считается матом")
        void doesNotMatchInsideWord() {
            assertEquals("сукач и хуйня-не-мат",
                    allowed(moderator().check(UUID.randomUUID(), "сукач и хуйня-не-мат", NO_BYPASS)));
        }

        @Test
        void bypassPermissionSkipsSwearFilter() {
            assertEquals("ты сука", allowed(
                    moderator().check(UUID.randomUUID(), "ты сука", bypassAll())));
        }

        @Test
        @DisplayName("swearReplaceInsteadOfBlock=false блокирует")
        void canBlockInsteadOfCensor() {
            ChatModerator strict = new ChatModerator(config("""
                    moderation:
                      cooldown: 0
                      swearReplaceInsteadOfBlock: false
                    """));

            assertEquals(ChatModerator.Result.SWEAR_BLOCK,
                    blocked(strict.check(UUID.randomUUID(), "ты сука", NO_BYPASS)));
        }
    }

    @Nested
    @DisplayName("Спам и капс")
    class Spam {

        @Test
        void blocksDuplicateMessage() {
            ChatModerator moderator = moderator();
            UUID player = UUID.randomUUID();

            moderator.record(player, "привет мир");
            assertEquals(ChatModerator.Result.DUPLICATE,
                    blocked(moderator.check(player, "привет мир", NO_BYPASS)));
        }

        @Test
        @DisplayName("дубль без учёта регистра")
        void blocksDuplicateIgnoringCase() {
            ChatModerator moderator = moderator();
            UUID player = UUID.randomUUID();

            moderator.record(player, "Привет мир");
            assertEquals(ChatModerator.Result.DUPLICATE,
                    blocked(moderator.check(player, "привет МИР", NO_BYPASS)));
        }

        @Test
        void blocksSimilarMessage() {
            ChatModerator moderator = moderator();
            UUID player = UUID.randomUUID();

            moderator.record(player, "привет мир как дела");
            assertEquals(ChatModerator.Result.SIMILAR,
                    blocked(moderator.check(player, "привет мир как дела!", NO_BYPASS)));
        }

        @Test
        @DisplayName("совсем другое сообщение не считается похожим")
        void allowsDifferentMessage() {
            ChatModerator moderator = moderator();
            UUID player = UUID.randomUUID();

            moderator.record(player, "привет мир");
            assertEquals("кто идёт в шахту",
                    allowed(moderator.check(player, "кто идёт в шахту", NO_BYPASS)));
        }

        @Test
        void blocksFlood() {
            assertEquals(ChatModerator.Result.FLOOD,
                    blocked(moderator().check(UUID.randomUUID(), "ааааааа", NO_BYPASS)));
        }

        @Test
        void lowercasesShouting() {
            assertEquals("привет мир как дела", allowed(moderator().check(
                    UUID.randomUUID(), "ПРИВЕТ МИР КАК ДЕЛА", NO_BYPASS)));
        }

        @Test
        @DisplayName("короткое сообщение анти-капс не трогает")
        void keepsShortUppercaseMessage() {
            assertEquals("ЛОЛ", allowed(moderator().check(UUID.randomUUID(), "ЛОЛ", NO_BYPASS)));
        }

        @Test
        void blocksCrashCharacters() {
            assertEquals(ChatModerator.Result.CRASH,
                    blocked(moderator().check(UUID.randomUUID(), "привет\u0007", NO_BYPASS)));
        }

        @Test
        void bypassPermissionSkipsSpamChecks() {
            ChatModerator moderator = moderator();
            UUID player = UUID.randomUUID();

            moderator.record(player, "привет мир");
            assertEquals("привет мир",
                    allowed(moderator.check(player, "привет мир", bypassAll())));
        }
    }

    @Nested
    @DisplayName("Кулдаун")
    class Cooldown {

        @Test
        void blocksSecondMessageAndReportsRemainingSeconds() {
            ChatModerator moderator = new ChatModerator(config("""
                    moderation:
                      cooldown: 5
                    """));
            UUID player = UUID.randomUUID();

            moderator.record(player, "первое");

            var blocked = assertInstanceOf(ChatModerator.Outcome.Blocked.class,
                    moderator.check(player, "второе", NO_BYPASS));

            assertEquals(ChatModerator.Result.COOLDOWN, blocked.reason());
            assertEquals(5, blocked.cooldownRemaining());
        }

        @Test
        @DisplayName("clear() сбрасывает состояние игрока")
        void clearResetsState() {
            ChatModerator moderator = new ChatModerator(config("""
                    moderation:
                      cooldown: 5
                    """));
            UUID player = UUID.randomUUID();

            moderator.record(player, "первое");
            moderator.clear(player);

            assertEquals("второе", allowed(moderator.check(player, "второе", NO_BYPASS)));
        }

        @Test
        @DisplayName("bypass.cooldown отключает проверку")
        void bypassSkipsCooldown() {
            ChatModerator moderator = new ChatModerator(config("""
                    moderation:
                      cooldown: 5
                    """));
            UUID player = UUID.randomUUID();

            moderator.record(player, "первое");
            assertEquals("второе", allowed(moderator.check(player, "второе", bypassAll())));
        }
    }

    @Test
    @DisplayName("обычное сообщение проходит без изменений")
    void allowsCleanMessage() {
        String message = "всем привет, кто идёт на спавн?";
        assertEquals(message, allowed(moderator().check(UUID.randomUUID(), message, NO_BYPASS)));
    }

    @Test
    @DisplayName("reload подменяет правила без пересоздания модератора")
    void reloadAppliesNewConfig() {
        ChatModerator moderator = moderator();

        moderator.reload(config("""
                moderation:
                  cooldown: 0
                  swearWords: ["банан"]
                  swearReplacement: "[censored]"
                """));

        assertEquals("ты [censored]", censored(
                moderator.check(UUID.randomUUID(), "ты банан", NO_BYPASS)));
    }
}
