package ru.net.terracraft.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkinHeadCache {

    private final JavaPlugin plugin;

    // Оставляем кэш на будущее (например, для проверки, что скин загружен),
    // но для тега он больше не нужен.
    private final Map<UUID, Boolean> loadedCache = new ConcurrentHashMap<>();

    public SkinHeadCache(JavaPlugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) {
            plugin.getLogger().info("SkinsRestorer не найден. Головы будут отображаться по UUID.");
        }
    }

    /**
     * Раньше тут был async-запрос к SkinsRestorer.
     * Теперь не нужен: Minecraft сам резолвит UUID через SkinsRestorer.
     */
    public void refresh(Player player) {
        // Пусто — можно оставить для совместимости с PlayerDataListener,
        // чтобы не менять сигнатуры.
    }

    public void remove(Player player) {
        loadedCache.remove(player.getUniqueId());
    }

    /**
     * Возвращает тег головы по UUID.
     * Minecraft/SkinsRestorer сами подтянут скин.
     */
    public String getHeadTag(Player player) {
        return "<head:uuid:" + player.getUniqueId() + ">";
    }
}