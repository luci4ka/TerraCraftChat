package ru.terracraft.chat.manager;

import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.Tasks;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IgnoreManager {

    private final TerraCraftChatPlugin plugin;
    private final StorageManager storage;
    private final Map<UUID, Set<UUID>> cache = new ConcurrentHashMap<>();

    public IgnoreManager(TerraCraftChatPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /**
     * Загружает игнор-лист игрока АСИНХРОННО.
     * До завершения isIgnoring(uuid, source) возвращает false.
     */
    public void load(Player player) {
        UUID uuid = player.getUniqueId();

        // Ставим пустой set сразу, чтобы isIgnoring не NPE
        cache.putIfAbsent(uuid, ConcurrentHashMap.newKeySet());

        if (!storage.isAvailable()) return;

        Tasks.supply(plugin, "ignores/" + player.getName(), () -> storage.getIgnores(uuid))
                .thenAccept(loaded -> {
                    if (loaded == null) return;
                    // Если игрок уже вышел — не кладём в кэш
                    if (!player.isOnline()) return;

                    Set<UUID> set = ConcurrentHashMap.newKeySet();
                    set.addAll(loaded);
                    cache.put(uuid, set);
                });
    }

    public void unload(Player player) {
        cache.remove(player.getUniqueId());
    }

    public boolean isIgnoring(UUID viewer, UUID source) {
        Set<UUID> ignored = cache.get(viewer);
        return ignored != null && ignored.contains(source);
    }

    public boolean isIgnoring(Player viewer, Player source) {
        return isIgnoring(viewer.getUniqueId(), source.getUniqueId());
    }

    public void addIgnore(Player viewer, UUID target) {
        addIgnore(viewer, target, 0L);
    }

    public void addIgnore(Player viewer, UUID target, long ttlMs) {
        if (viewer.getUniqueId().equals(target)) return;
        long expiresAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : 0L;
        cache.computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(target);
        storage.addIgnore(viewer.getUniqueId(), target, expiresAt);
    }

    public void removeIgnore(Player viewer, UUID target) {
        Set<UUID> ignored = cache.get(viewer.getUniqueId());
        if (ignored != null) {
            ignored.remove(target);
            storage.removeIgnore(viewer.getUniqueId(), target);
        }
    }

    public void addIgnore(Player viewer, Player target) {
        addIgnore(viewer, target.getUniqueId());
    }

    public void removeIgnore(Player viewer, Player target) {
        removeIgnore(viewer, target.getUniqueId());
    }

    public Set<UUID> getIgnoredByUuid(UUID playerId) {
        Set<UUID> ignored = cache.get(playerId);
        return ignored == null ? Collections.emptySet() : Set.copyOf(ignored);
    }

    public boolean isIgnoringAny(UUID playerId) {
        Set<UUID> ignored = cache.get(playerId);
        return ignored != null && !ignored.isEmpty();
    }
}
