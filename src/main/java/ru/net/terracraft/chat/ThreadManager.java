package ru.net.terracraft.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadManager {

    private final JavaPlugin plugin;
    private final ChatMessageRegistry registry;
    private final ChatFormatter formatter;

    // Кто сейчас отвечает в какой тред
    private final Map<UUID, UUID> activeThread = new ConcurrentHashMap<>();

    public ThreadManager(JavaPlugin plugin, ChatMessageRegistry registry, ChatFormatter formatter) {
        this.plugin = plugin;
        this.registry = registry;
        this.formatter = formatter;
    }

    public void setActive(Player player, UUID parentMessageId) {
        activeThread.put(player.getUniqueId(), parentMessageId);
    }

    public void clearActive(Player player) {
        activeThread.remove(player.getUniqueId());
    }

    public UUID getActive(Player player) {
        return activeThread.get(player.getUniqueId());
    }

    public boolean isActive(Player player) {
        return activeThread.containsKey(player.getUniqueId());
    }

    /**
     * Публикует ответ в тред. Возвращает true, если ответ был отправлен.
     */
    public boolean postReply(Player sender, String text) {
        UUID parentId = activeThread.get(sender.getUniqueId());
        if (parentId == null) return false;

        Optional<ChatMessage> opt = registry.get(parentId);
        if (opt.isEmpty()) {
            clearActive(sender);
            return false;
        }

        ChatMessage parent = opt.get();

        ChatMessage.ThreadReply reply = new ChatMessage.ThreadReply(
                sender.getUniqueId(), sender.getName(), text
        );
        parent.addReply(reply);

        // Рассылаем reply всем, кто видел родителя
        Component formatted = formatter.formatThreadReply(parent, reply);
        for (UUID viewerId : parent.getViewers()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                viewer.sendMessage(formatted);
            }
        }

        clearActive(sender);

        return true;
    }

    /**
     * Сбрасывает активные треды у офлайн-игроков.
     */
    public void cleanupOffline() {
        activeThread.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
    }
}