package ru.net.terracraft.chat;

import net.skinsrestorer.api.PropertyUtils;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkinHeadCache {

    private final JavaPlugin plugin;
    private final Map<UUID, String> textureIdCache = new ConcurrentHashMap<>();
    private final boolean skinsRestorerAvailable;

    public SkinHeadCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.skinsRestorerAvailable = Bukkit.getPluginManager().getPlugin("SkinsRestorer") != null;

        if (!skinsRestorerAvailable) {
            plugin.getLogger().info("SkinsRestorer не найден. Головы будут отображаться по имени.");
        }
    }

    /**
     * Асинхронно загружает texture ID игрока и кладёт в кэш.
     * Вызывать при входе игрока и при смене скина.
     */
    public void refresh(Player player) {
        if (!skinsRestorerAvailable) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Уходим в async — getSkinForPlayer может делать блокирующие запросы
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SkinsRestorer api = SkinsRestorerProvider.get();
                Optional<SkinProperty> skinOpt = api.getPlayerStorage()
                        .getSkinForPlayer(uuid, name);

                if (skinOpt.isEmpty()) {
                    // Нет скина — чистим кэш, fallback на имя
                    textureIdCache.remove(uuid);
                    return;
                }

                String textureUrl = PropertyUtils.getSkinTextureUrl(skinOpt.get());
                // URL: https://textures.minecraft.net/texture/<hash>
                String hash = textureUrl.substring(textureUrl.lastIndexOf('/') + 1);
                textureIdCache.put(uuid, hash);

            } catch (Exception e) {
                // DataRequestException или любая другая ошибка — не роняем плагин
                plugin.getLogger().warning("Не удалось получить скин для " + name + ": " + e.getMessage());
                textureIdCache.remove(uuid);
            }
        });
    }

    /**
     * Убирает игрока из кэша при выходе.
     */
    public void remove(Player player) {
        textureIdCache.remove(player.getUniqueId());
    }

    /**
     * Возвращает готовый MiniMessage-тег для головы игрока.
     * Если есть texture ID — <head:texture_hash>, иначе fallback на <head:name>.
     */
    public String getHeadTag(Player player) {
        String hash = textureIdCache.get(player.getUniqueId());
        if (hash != null) {
            return "<head:texture:" + hash + ">";
        }
        return "<head:name:" + player.getName() + ">";
    }
}