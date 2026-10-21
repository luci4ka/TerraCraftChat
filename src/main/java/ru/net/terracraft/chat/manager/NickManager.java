package ru.net.terracraft.chat.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.format.NickFormat;
import ru.net.terracraft.chat.storage.StorageManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NickManager {
    private final StorageManager storage;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public NickManager(JavaPlugin plugin, StorageManager storage) {
        this.storage = storage;
    }

    public void load(Player player) {
        String nick = storage.getNick(player.getUniqueId());
        if (nick != null && !nick.isEmpty()) {
            cache.put(player.getUniqueId(), nick);
        }
    }

    public void setNick(Player player, String nick) {
        cache.put(player.getUniqueId(), nick);
        storage.setNick(player.getUniqueId(), nick);
        updateDisplayName(player);
    }

    public void resetNick(Player player) {
        cache.remove(player.getUniqueId());
        storage.removeNick(player.getUniqueId());
        player.displayName(Component.text(player.getName()));
    }

    public String getNick(Player player) {
        return cache.get(player.getUniqueId());
    }

    public Component getNickComponent(Player player) {
        String raw = cache.get(player.getUniqueId());
        if (raw == null || raw.isEmpty()) return null;
        return NickFormat.SAFE.deserialize(raw);
    }

    private void updateDisplayName(Player player) {
        Component nick = getNickComponent(player);
        player.displayName(nick != null ? nick : Component.text(player.getName()));
    }

    public void unload(Player player) {
        cache.remove(player.getUniqueId());
    }
}