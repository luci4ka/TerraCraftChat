package ru.terracraft.chat.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import java.util.UUID;

/**
 * Сборка «головы игрока» как объектного компонента (MC 1.21.9+).
 *
 * <p>Чистый класс без Bukkit — покрывается юнит-тестами.
 *
 * <p>Важно: тег MiniMessage {@code <head:...>} принимает <b>один</b> аргумент
 * (uuid, texture-key или ник) и опциональный {@code hat}. Вариантов
 * {@code <head:uuid:...>} / {@code <head:name:...>} не существует — такой текст
 * разбирается как ник «uuid»/«name» и даёт голову несуществующего игрока.
 * Поэтому компонент собирается через API, а в формат подставляется
 * через {@code Placeholder.component(...)}.
 */
public final class HeadComponents {

    /** Имя стандартного профильного свойства со скином. */
    public static final String TEXTURES = "textures";

    private HeadComponents() {}

    /**
     * Голова по UUID: скин резолвит клиент (online-mode).
     */
    public static Component byUuid(UUID uuid, boolean asHat) {
        if (uuid == null) return Component.empty();
        return Component.object(head(ObjectContents.playerHead(uuid), asHat));
    }

    /**
     * Голова по готовому профильному свойству {@code textures}
     * (value + signature из SkinsRestorer) — единственный корректный способ
     * для offline-mode серверов.
     */
    public static Component byProperty(String name, String value, String signature, boolean asHat) {
        if (value == null || value.isBlank()) {
            return Component.empty();
        }
        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead();
        if (name != null && PlayerHeadObjectContents.isValidName(name)) {
            builder.name(name);
        }
        builder.profileProperty(PlayerHeadObjectContents.property(TEXTURES, value, signature));
        return Component.object(head(builder, asHat));
    }

    private static PlayerHeadObjectContents head(PlayerHeadObjectContents contents, boolean asHat) {
        return contents.hat() == asHat ? contents : contents.toBuilder().hat(asHat).build();
    }

    private static PlayerHeadObjectContents head(PlayerHeadObjectContents.Builder builder, boolean asHat) {
        return builder.hat(asHat).build();
    }
}
