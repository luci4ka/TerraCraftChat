package ru.terracraft.chat.format;

import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;

import java.util.Optional;
import java.util.UUID;

/**
 * Изолированный мост к SkinsRestorer API.
 *
 * <p>Класс существует отдельно, чтобы JVM не трогала классы SkinsRestorer,
 * когда плагина нет на сервере (зависимость объявлена как
 * {@code required: false} + {@code join-classpath: true}).
 *
 * <p>Все методы вызываются только с виртуальных потоков
 * ({@link ru.terracraft.chat.util.Tasks}) — {@code getSkinForPlayer}
 * может ходить в сеть (Mojang), и на main-потоке ему делать нечего.
 */
final class SkinsRestorerBridge {

    private SkinsRestorerBridge() {}

    /** Есть ли на сервере живой API SkinsRestorer. */
    static boolean available() {
        try {
            return SkinsRestorerProvider.get() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Возвращает свойство {@code textures} игрока: сначала из локального
     * хранилища SkinsRestorer (быстро), затем — с запросом к Mojang.
     *
     * @return свойство или {@code null}, если скин недоступен
     */
    static PlayerHeadObjectContents.ProfileProperty fetch(UUID uuid, String playerName) {
        try {
            SkinsRestorer api = SkinsRestorerProvider.get();
            if (api == null) return null;

            var storage = api.getPlayerStorage();

            Optional<SkinProperty> skin = storage.getSkinOfPlayer(uuid);
            if (skin.isEmpty() && playerName != null && !playerName.isEmpty()) {
                skin = storage.getSkinForPlayer(uuid, playerName);
            }

            return skin.map(SkinsRestorerBridge::toProperty).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static PlayerHeadObjectContents.ProfileProperty toProperty(SkinProperty property) {
        return PlayerHeadObjectContents.property(
                SkinProperty.TEXTURES_NAME,
                property.getValue(),
                property.getSignature()
        );
    }
}
