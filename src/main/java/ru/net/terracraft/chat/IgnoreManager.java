package ru.net.terracraft.chat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class IgnoreManager {

    private final JavaPlugin plugin;
    private final NamespacedKey ignoreKey;

    // Кэш в памяти для быстрого доступа
    private final Map<UUID, Set<UUID>> ignoreCache = new HashMap<>();

    public IgnoreManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // NamespacedKey: plugin name автоматически становится namespace
        this.ignoreKey = new NamespacedKey(plugin, "ignored_players");
    }

    /**
     * Загружает игнор-лист игрока из PDC в кэш.
     * Вызывать при входе игрока.
     */
    public void load(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String raw = pdc.getOrDefault(ignoreKey, PersistentDataType.STRING, "");

        Set<UUID> ignored = new HashSet<>();
        if (!raw.isEmpty()) {
            for (String part : raw.split(",")) {
                try {
                    ignored.add(UUID.fromString(part.trim()));
                } catch (IllegalArgumentException ignoredEx) {
                    // пропускаем битые записи
                }
            }
        }
        ignoreCache.put(player.getUniqueId(), ignored);
    }

    /**
     * Сохраняет игнор-лист игрока в PDC.
     * Вызывать при выходе игрока или после каждого изменения.
     */
    public void save(Player player) {
        Set<UUID> ignored = ignoreCache.get(player.getUniqueId());
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        if (ignored == null || ignored.isEmpty()) {
            pdc.remove(ignoreKey); // чистим, если пусто
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (UUID uuid : ignored) {
            if (sb.length() > 0) sb.append(",");
            sb.append(uuid.toString());
        }

        pdc.set(ignoreKey, PersistentDataType.STRING, sb.toString());
    }

    /**
     * Удаляет игрока из кэша при выходе.
     */
    public void unload(Player player) {
        save(player); // на всякий случай
        ignoreCache.remove(player.getUniqueId());
    }

    // --- Работа с игнором (используем кэш) ---

    public boolean isIgnoring(Player viewer, Player source) {
        return isIgnoring(viewer.getUniqueId(), source.getUniqueId());
    }

    public boolean isIgnoring(UUID viewer, UUID source) {
        Set<UUID> ignored = ignoreCache.get(viewer);
        return ignored != null && ignored.contains(source);
    }

    public void addIgnore(Player viewer, Player target) {
        ignoreCache.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>())
                .add(target.getUniqueId());
        save(viewer); // сразу сохраняем
    }

    public void removeIgnore(Player viewer, Player target) {
        Set<UUID> ignored = ignoreCache.get(viewer.getUniqueId());
        if (ignored != null) {
            ignored.remove(target.getUniqueId());
            save(viewer);
        }
    }

    public Set<UUID> getIgnoredByUuid(UUID playerId) {
        return ignoreCache.getOrDefault(playerId, Collections.emptySet());
    }

    public boolean isIgnoringAny(UUID playerId) {
        Set<UUID> ignored = ignoreCache.get(playerId);
        return ignored != null && !ignored.isEmpty();
    }
}