package ru.terracraft.chat.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.terracraft.chat.format.HeadComponents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("ChatConfig — форматы по умолчанию")
class ChatConfigFormatsTest {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static TagResolver resolver() {
        UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        Component head = HeadComponents.byUuid(uuid, false);

        return TagResolver.builder()
                .resolver(Placeholder.component("sender_head", head))
                .resolver(Placeholder.component("receiver_head", head))
                .resolver(Placeholder.component("display_name", Component.text("Steve")))
                .resolver(Placeholder.component("sender", Component.text("Steve")))
                .resolver(Placeholder.component("receiver", Component.text("Alex")))
                .resolver(Placeholder.component("player", Component.text("Steve")))
                .resolver(Placeholder.component("message", Component.text("привет")))
                .resolver(Placeholder.unparsed("sender_name", "Steve"))
                .resolver(Placeholder.unparsed("world", "world"))
                .resolver(Placeholder.unparsed("type", "MSG"))
                .resolver(Placeholder.unparsed("group", "шахтёры"))
                .resolver(Placeholder.unparsed("result", "5"))
                .resolver(Placeholder.unparsed("max", "6"))
                .resolver(Placeholder.unparsed("id", "global"))
                .resolver(Placeholder.unparsed("channel", "global"))
                .resolver(Placeholder.unparsed("count", "3"))
                .resolver(Placeholder.unparsed("seconds", "2"))
                .build();
    }

    private static Map<String, String> defaultFormats() {
        ChatConfig.FormatsSection formats = new ChatConfig().formats();

        Map<String, String> map = new LinkedHashMap<>();
        map.put("local", formats.local());
        map.put("global", formats.global());
        map.put("staff", formats.staff());
        map.put("privateMsg", formats.privateMsg());
        map.put("reply", formats.reply());
        map.put("broadcast", formats.broadcast());
        map.put("noOneNearby", formats.noOneNearby());
        return map;
    }

    @Test
    @DisplayName("каждый формат по умолчанию — валидный MiniMessage")
    void defaultFormatsAreValidMiniMessage() {
        Map<String, String> formats = defaultFormats();

        formats.forEach((name, format) -> assertDoesNotThrow(
                () -> assertNotNull(MINI_MESSAGE.deserialize(format, resolver()), name),
                "формат '" + name + "' не парсится: " + format
        ));
    }

    @Test
    @DisplayName("в формате публичного чата есть и голова, и текст сообщения")
    void localFormatContainsRequiredPlaceholders() {
        String local = new ChatConfig().formats().local();

        assertAll(
                () -> assertEquals(true, local.contains("<sender_head>"), "нет <sender_head>"),
                () -> assertEquals(true, local.contains("<message>"), "нет <message>"),
                () -> assertEquals(true, local.contains("{msg_command}"), "нет {msg_command}")
        );
    }

    @Test
    @DisplayName("неизвестный ключ формата откатывается к local")
    void unknownFormatKeyFallsBackToLocal() {
        ChatConfig.FormatsSection formats = new ChatConfig().formats();

        assertEquals(formats.local(), formats.byKey("не-существует"));
        assertEquals(formats.local(), formats.byKey(null));
        assertEquals(formats.global(), formats.byKey("global"));
    }

    @Test
    @DisplayName("значения по умолчанию ключевых секций")
    void defaultSectionValues() {
        ChatConfig config = new ChatConfig();

        assertAll(
                () -> assertEquals("local", config.channels().defaultChannel()),
                () -> assertEquals(3, config.moderation().cooldown()),
                () -> assertEquals(200, config.delete().cacheSize()),
                () -> assertEquals(true, config.display().showPlayerHeads()),
                () -> assertEquals(false, config.display().headAsHat(),
                        "по умолчанию голова плоская (как аватар)"),
                () -> assertEquals(100, config.channels().channels().get("local").range()),
                () -> assertEquals("terracraftchat.channel.staff",
                        config.channels().channels().get("staff").permission())
        );
    }
}
