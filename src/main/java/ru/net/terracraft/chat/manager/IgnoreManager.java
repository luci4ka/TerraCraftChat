package ru.net.terracraft.chat.manager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.storage.StorageManager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IgnoreManager {

    private final JavaPlugin plugin;
    private final StorageManager storage;
    private final Map<UUID, Set<UUID>> cache = new ConcurrentHashMap<>();

    public IgnoreManager(JavaPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load(Player player) {
        cache.put(player.getUniqueId(), storage.getIgnores(player.getUniqueId()));
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
        return ignored == null ? Collections.emptySet() : Collections.unmodifiableSet(ignored);
    }

    public boolean isIgnoringAny(UUID playerId) {
        Set<UUID> ignored = cache.get(playerId);
        return ignored != null && !ignored.isEmpty();
    }
}