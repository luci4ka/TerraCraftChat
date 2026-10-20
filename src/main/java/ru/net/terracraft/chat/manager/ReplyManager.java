package ru.net.terracraft.chat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.storage.StorageManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReplyManager {
    private final JavaPlugin plugin;
    private final StorageManager storage;
    private final Map<UUID, UUID> cache = new ConcurrentHashMap<>();

    public ReplyManager(JavaPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void setLastReply(Player viewer, Player target) {
        cache.put(viewer.getUniqueId(), target.getUniqueId());
        storage.setReply(viewer.getUniqueId(), target.getUniqueId());
    }

    public Player getLastReply(Player viewer) {
        UUID targetId = cache.get(viewer.getUniqueId());
        if (targetId == null) {
            targetId = storage.getReply(viewer.getUniqueId());
            if (targetId != null) cache.put(viewer.getUniqueId(), targetId);
        }
        return targetId != null ? Bukkit.getPlayer(targetId) : null;
    }

    public void clear(Player viewer) {
        cache.remove(viewer.getUniqueId());
        storage.clearReply(viewer.getUniqueId());
    }
}