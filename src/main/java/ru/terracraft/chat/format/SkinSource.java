package ru.terracraft.chat.format;

import java.util.UUID;

/**
 * Источник скинов игрока.
 *
 * <p>Публичный фасад без типов SkinsRestorer в сигнатурах: весь код,
 * зависящий от SkinsRestorer, спрятан в {@link SkinsRestorerBridge}.
 * Поэтому класс можно безопасно использовать, когда плагина нет на сервере
 * (проверять через {@link #available()}).
 */
public final class SkinSource {

    /** Готовое профильное свойство {@code textures}. */
    public record Skin(String value, String signature) {}

    private SkinSource() {}

    /** Есть ли на сервере SkinsRestorer с инициализированным API. */
    public static boolean available() {
        return SkinsRestorerBridge.available();
    }

    /**
     * Загружает скин игрока (блокирующая операция — только с
     * {@link ru.terracraft.chat.util.Tasks#supply}).
     *
     * @return скин или {@code null}, если его нет/плагин отсутствует
     */
    public static Skin fetch(UUID uuid, String playerName) {
        if (uuid == null) return null;

        var property = SkinsRestorerBridge.fetch(uuid, playerName);
        if (property == null) return null;

        return new Skin(property.value(), property.signature());
    }
}
