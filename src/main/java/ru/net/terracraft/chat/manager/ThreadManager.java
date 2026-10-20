package ru.net.terracraft.chat.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.config.ChatConfig;
import ru.net.terracraft.chat.format.ChatFormatter;
import ru.net.terracraft.chat.model.ChatMessage;
import ru.net.terracraft.chat.util.MessageUtil;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadManager {

    private final TerraCraftChatPlugin plugin;
    private final ChatMessageRegistry registry;
    private ChatFormatter formatter;
    private ChatConfig config;

    private final Map<UUID, UUID> activeThread = new ConcurrentHashMap<>();

    public ThreadManager(TerraCraftChatPlugin plugin,
                         ChatMessageRegistry registry,
                         ChatFormatter formatter,
                         ChatConfig config) {
        this.plugin = plugin;
        this.registry = registry;
        this.formatter = formatter;
        this.config = config;
    }

    public void reload(ChatConfig newConfig, ChatFormatter newFormatter) {
        this.config = newConfig;
        this.formatter = newFormatter;
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

    public boolean postReply(Player sender, String text) {
        if (!config.threads().enabled()) {
            clearActive(sender);
            MessageUtil.sendPrefixed(sender, "chat.thread-disabled");
            return false;
        }

        UUID parentId = activeThread.get(sender.getUniqueId());
        if (parentId == null) return false;

        Optional<ChatMessage> opt = registry.get(parentId);
        if (opt.isEmpty()) {
            clearActive(sender);
            MessageUtil.sendPrefixed(sender, "chat.message-not-found");
            return false;
        }

        ChatMessage parent = opt.get();

        if (!parent.wasSeenBy(sender.getUniqueId())) {
            clearActive(sender);
            MessageUtil.sendPrefixed(sender, "chat.thread-no-access");
            return false;
        }

        int maxReplies = config.threads().maxThreadReplies();
        if (maxReplies > 0 && parent.getThreadSize() >= maxReplies) {
            clearActive(sender);
            MessageUtil.sendPrefixed(sender, "chat.thread-limit");
            return false;
        }

        ChatMessage.ThreadReply reply = new ChatMessage.ThreadReply(
                sender.getUniqueId(), sender.getName(), text
        );
        parent.addReply(reply);

        // Сохраняем в БД (async)
        var storage = plugin.getStorageManager();
        if (storage != null) {
            storage.saveThreadReply(
                    reply.getId(), parentId,
                    reply.getAuthorId(), reply.getAuthorName(),
                    reply.getText(), reply.getTimestamp()
            );
        }

        if (plugin.getChatLogger() != null) {
            plugin.getChatLogger().log("THREAD", sender.getName(),
                    null, "→ " + parent.getSenderName() + ": " + text);
        }

        Component formatted = formatter.formatThreadReply(parent, reply);
        var ignoreManager = plugin.getIgnoreManager();

        for (UUID viewerId : parent.getViewers()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) continue;
            if (ignoreManager.isIgnoring(viewer, sender)) continue;
            viewer.sendMessage(formatted);
        }

        clearActive(sender);
        return true;
    }

    public void cleanupOffline() {
        activeThread.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null);
    }
}