package ru.net.terracraft.chat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReplyManager {

    private final JavaPlugin plugin;
    // viewer -> target (кому последний раз писали или от кого получали)
    private final Map<UUID, UUID> lastReply = new HashMap<>();

    public ReplyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Запомнить собеседника. Вызывается и при отправке, и при получении ЛС —
     * тогда /reply будет работать в обе стороны.
     */
    public void setLastReply(Player viewer, Player target) {
        lastReply.put(viewer.getUniqueId(), target.getUniqueId());
    }

    /**
     * Получить последнего собеседника (онлайн).
     */
    public Player getLastReply(Player viewer) {
        UUID targetId = lastReply.get(viewer.getUniqueId());
        if (targetId == null) {
            return null;
        }
        return plugin.getServer().getPlayer(targetId);
    }

    /**
     * Очистить запись (например, при выходе игрока).
     */
    public void clear(Player viewer) {
        lastReply.remove(viewer.getUniqueId());
    }
}