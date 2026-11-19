package ru.terracraft.chat.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.format.HeadComponents;
import ru.terracraft.chat.format.SkinSource;
import ru.terracraft.chat.util.Tasks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш скинов и сборка головы игрока для чата.
 *
 * <p>Если на сервере есть SkinsRestorer — скин берётся из него
 * (свойство {@code textures}: value + signature) и передаётся в компонент
 * головы. Это работает и в offline-mode, где UUID не совпадает с аккаунтом
 * Mojang. Если SkinsRestorer нет — используется голова по UUID
 * (скин резолвит клиент, актуально для online-mode).
 *
 * <p>Загрузка скина никогда не выполняется на main-потоке: сначала отдаём
 * голову по UUID, а как только скин подтянулся — следующие сообщения
 * уже идут с корректной текстурой.
 */
public class SkinHeadCache {

    /** Скин может измениться — обновляем не чаще, чем раз в 10 минут. */
    private static final long TTL_MS = 10 * 60 * 1000L;

    private final TerraCraftChatPlugin plugin;
    private final boolean skinsRestorerPresent;

    private final Map<UUID, CachedSkin> cache = new ConcurrentHashMap<>();

    private record CachedSkin(String name, String value, String signature, long expiresAt) {
        boolean valid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    public SkinHeadCache(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
        this.skinsRestorerPresent = Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null
                && SkinSource.available();

        if (skinsRestorerPresent) {
            plugin.getLogger().info("SkinsRestorer найден — головы в чате берутся из его хранилища скинов.");
        } else {
            plugin.getLogger().info("SkinsRestorer не найден — головы строятся по UUID игрока.");
        }
    }

    public boolean usesSkinsRestorer() {
        return skinsRestorerPresent;
    }

    /**
     * Готовая голова для подстановки в формат
     * ({@code Placeholder.component("sender_head", ...)}).
     * Никогда не блокирует и никогда не бросает исключений.
     */
    public Component head(Player player) {
        if (player == null) return Component.empty();

        var display = plugin.getChatConfig().display();
        if (!display.showPlayerHeads()) return Component.empty();

        boolean asHat = display.headAsHat();

        CachedSkin skin = cache.get(player.getUniqueId());
        if (skin != null && skin.valid()) {
            Component head = HeadComponents.byProperty(
                    skin.name(), skin.value(), skin.signature(), asHat);
            if (head != Component.empty()) return head;
        }

        return HeadComponents.byUuid(player.getUniqueId(), asHat);
    }

    /**
     * Асинхронно обновляет скин игрока (вызывается при входе и после смены скина).
     */
    public void refresh(Player player) {
        if (!skinsRestorerPresent || player == null) return;

        UUID uuid = player.getUniqueId();
        String name = player.getName();

        Tasks.supply(plugin, "skins/" + name, () -> SkinSource.fetch(uuid, name))
                .thenAccept(property -> {
                    if (property == null) return;
                    cache.put(uuid, new CachedSkin(
                            name,
                            property.value(),
                            property.signature(),
                            System.currentTimeMillis() + TTL_MS
                    ));
                });
    }

    /** Сбрасывает кэш для игрока (например, после /skin set). */
    public void invalidate(UUID uuid) {
        if (uuid != null) cache.remove(uuid);
    }

    public void remove(Player player) {
        if (player != null) cache.remove(player.getUniqueId());
    }

    /** Полный сброс (reload конфига). */
    public void invalidateAll() {
        cache.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            refresh(online);
        }
    }

    /** Текстура для внешних потребителей (PAPI-плейсхолдер и т.п.). */
    public PlayerHeadObjectContents.ProfileProperty cachedProperty(UUID uuid) {
        CachedSkin skin = cache.get(uuid);
        if (skin == null || !skin.valid()) return null;
        return PlayerHeadObjectContents.property(HeadComponents.TEXTURES, skin.value(), skin.signature());
    }
}
