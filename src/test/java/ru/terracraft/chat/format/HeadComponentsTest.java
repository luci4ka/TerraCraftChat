package ru.terracraft.chat.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HeadComponents — головы игроков как компоненты")
class HeadComponentsTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private static PlayerHeadObjectContents contents(Component component) {
        ObjectComponent object = assertInstanceOf(ObjectComponent.class, component);
        return assertInstanceOf(PlayerHeadObjectContents.class, object.contents());
    }

    @Test
    @DisplayName("голова по UUID содержит id игрока")
    void buildsHeadByUuid() {
        PlayerHeadObjectContents contents = contents(HeadComponents.byUuid(NOTCH, false));

        assertEquals(NOTCH, contents.id());
        assertNull(contents.name(), "имя не должно подставляться — иначе клиент ищет профиль по нику");
        assertFalse(contents.hat());
    }

    @Test
    @DisplayName("hat=true сохраняется")
    void honoursHatFlag() {
        assertTrue(contents(HeadComponents.byUuid(NOTCH, true)).hat());
    }

    @Test
    @DisplayName("голова по текстуре несёт свойство textures (value + signature)")
    void buildsHeadFromProfileProperty() {
        PlayerHeadObjectContents contents = contents(
                HeadComponents.byProperty("Notch", "BASE64_VALUE", "SIGNATURE", false));

        assertEquals("Notch", contents.name());
        assertEquals(1, contents.profileProperties().size());

        var property = contents.profileProperties().getFirst();
        assertEquals("textures", property.name());
        assertEquals("BASE64_VALUE", property.value());
        assertEquals("SIGNATURE", property.signature());
    }

    @Test
    @DisplayName("пустая текстура и null дают пустой компонент")
    void emptyPropertyProducesEmptyComponent() {
        assertEquals(Component.empty(), HeadComponents.byProperty("Notch", "", "sig", false));
        assertEquals(Component.empty(), HeadComponents.byProperty("Notch", null, null, false));
        assertEquals(Component.empty(), HeadComponents.byUuid(null, false));
    }

    @Test
    @DisplayName("компонент головы корректно подставляется в MiniMessage-формат")
    void headIsUsableAsPlaceholder() {
        Component format = MiniMessage.miniMessage().deserialize(
                "<gray>[<green>L<gray>] <sender_head> <display_name><gray>: <message>",
                net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.builder()
                        .resolver(Placeholder.component("sender_head", HeadComponents.byUuid(NOTCH, false)))
                        .resolver(Placeholder.component("display_name", Component.text("Notch")))
                        .resolver(Placeholder.component("message", Component.text("привет")))
                        .build()
        );

        assertNotNull(format);
        assertEquals("L", plainText(format).substring(1, 2));
    }

    @Test
    @DisplayName("РЕГРЕССИЯ: старый синтаксис <head:uuid:UUID> создавал голову игрока с ником «uuid»")
    void legacySyntaxWasBroken() {
        Component legacy = MiniMessage.miniMessage().deserialize("<head:uuid:" + NOTCH + ">");
        PlayerHeadObjectContents legacyContents = contents(legacy);

        assertNull(legacyContents.id(), "по старому синтаксису id не заполнялся");
        assertEquals("uuid", legacyContents.name(), "вместо скина подставлялся профиль с ником «uuid»");
    }

    @Test
    @DisplayName("новый синтаксис <head:UUID> даёт голову именно этого игрока")
    void uuidSyntaxWorks() {
        PlayerHeadObjectContents contents = contents(
                MiniMessage.miniMessage().deserialize("<head:" + NOTCH + ">"));

        assertEquals(NOTCH, contents.id());
    }

    private static String plainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component);
    }
}
