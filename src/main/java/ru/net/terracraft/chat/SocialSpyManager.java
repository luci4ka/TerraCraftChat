package ru.net.terracraft.chat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SocialSpyManager {

    private final JavaPlugin plugin;
    private final Set<UUID> enabled = new HashSet<>();

    public SocialSpyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Переключает режим. Возвращает новое состояние.
     */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabled.remove(uuid)) {
            return false; // был включён — выключили
        } else {
            enabled.add(uuid);
            return true; // был выключен — включили
        }
    }

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public void disable(Player player) {
        enabled.remove(player.getUniqueId());
    }

    public Set<UUID> getAll() {
        return enabled;
    }
}