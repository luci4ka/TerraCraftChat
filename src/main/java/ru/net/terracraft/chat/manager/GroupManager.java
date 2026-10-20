package ru.net.terracraft.chat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.storage.StorageManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {

    private final TerraCraftChatPlugin plugin;
    private final StorageManager storage;

    // Активная группа игрока — /chat group <name> без сообщения → выбор активной
    private final Map<UUID, UUID> activeGroupByPlayer = new ConcurrentHashMap<>();

    public GroupManager(TerraCraftChatPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public StorageManager.GroupRow getByName(String name) {
        return storage.getGroupByName(name);
    }

    public void setActive(Player player, UUID groupId) {
        if (groupId == null) {
            activeGroupByPlayer.remove(player.getUniqueId());
        } else {
            activeGroupByPlayer.put(player.getUniqueId(), groupId);
        }
    }

    public UUID getActive(Player player) {
        return activeGroupByPlayer.get(player.getUniqueId());
    }

    public void clearActive(Player player) {
        activeGroupByPlayer.remove(player.getUniqueId());
    }

    /**
     * Отправляет сообщение в группу всем онлайн-участникам.
     */
    public boolean sendToGroup(Player sender, StorageManager.GroupRow group, String message) {
        List<UUID> members = storage.getGroupMembers(group.id());

        // Проверяем, что sender — участник
        if (!members.contains(sender.getUniqueId())) {
            return false;
        }

        UUID msgId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        storage.saveGroupMessage(msgId, group.id(), sender.getUniqueId(),
                sender.getName(), message, now);

        for (UUID memberId : members) {
            Player viewer = Bukkit.getPlayer(memberId);
            if (viewer == null || !viewer.isOnline()) continue;
            if (plugin.getIgnoreManager().isIgnoring(viewer, sender)) continue;

            var formatted = plugin.getChatFormatter().formatGroupMessage(
                    sender, group.name(), message, viewer);
            viewer.sendMessage(formatted);
        }
        return true;
    }
}