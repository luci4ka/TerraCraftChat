package ru.net.terracraft.chat.manager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.storage.StorageManager;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SocialSpyManager {
    private final StorageManager storage;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public SocialSpyManager(JavaPlugin plugin, StorageManager storage) {
        this.storage = storage;
        // Загружаем при старте
        this.enabled.addAll(storage.getAllSocialSpy());
    }

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabled.remove(uuid)) {
            storage.setSocialSpy(uuid, false);
            return false;
        } else {
            enabled.add(uuid);
            storage.setSocialSpy(uuid, true);
            return true;
        }
    }

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public boolean isEnabled(UUID uuid) {
        return enabled.contains(uuid);
    }

    public void disable(Player player) {
        enabled.remove(player.getUniqueId());
        storage.setSocialSpy(player.getUniqueId(), false);
    }

    public Set<UUID> getAll() {
        return Collections.unmodifiableSet(enabled);
    }
}