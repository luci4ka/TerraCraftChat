package ru.terracraft.chat.manager;

import org.bukkit.plugin.java.JavaPlugin;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.model.ChatMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatMessageRegistry {

    private final JavaPlugin plugin;
    private volatile int maxMessages;
    private volatile long ttlMs;

    private final Deque<ChatMessage> messages = new ArrayDeque<>();
    private final Map<UUID, ChatMessage> byId = new ConcurrentHashMap<>();

    public ChatMessageRegistry(JavaPlugin plugin, int maxMessages, int ttlSeconds) {
        this.plugin = plugin;
        this.maxMessages = maxMessages;
        this.ttlMs = ttlSeconds * 1000L;
    }

    /**
     * Регистрирует сообщение + асинхронно подгружает тред из БД.
     * Используется при обычной отправке сообщения.
     */
    public ChatMessage register(ChatMessage msg) {
        return doRegister(msg, true);
    }

    /**
     * Регистрирует сообщение БЕЗ подгрузки треда из БД.
     * Используется при восстановлении истории при старте —
     * треды загружаются отдельно (batch).
     */
    public ChatMessage registerWithoutThreadLoad(ChatMessage msg) {
        return doRegister(msg, false);
    }

    private ChatMessage doRegister(ChatMessage msg, boolean loadThread) {
        synchronized (messages) {
            messages.addLast(msg);
            byId.put(msg.getId(), msg);

            while (messages.size() > maxMessages) {
                ChatMessage old = messages.pollFirst();
                if (old != null) byId.remove(old.getId());
            }

            long cutoff = System.currentTimeMillis() - ttlMs;
            while (!messages.isEmpty() && messages.peekFirst().getTimestamp() < cutoff) {
                ChatMessage old = messages.pollFirst();
                if (old != null) byId.remove(old.getId());
            }
        }

        // Асинхронно подгружаем тред из БД (для перезахода)
        if (loadThread) {
            var storage = ((TerraCraftChatPlugin) plugin).getStorageManager();
            if (storage != null) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        var rows = storage.getThreadReplies(msg.getId());
                        for (var row : rows) {
                            msg.addReply(new ChatMessage.ThreadReply(
                                    row.id(), row.authorUuid(), row.authorName(),
                                    row.text(), row.timestamp()
                            ));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Thread load failed: " + e.getMessage());
                    }
                });
            }
        }

        return msg;
    }

    public Optional<ChatMessage> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public void remove(UUID id) {
        synchronized (messages) {
            ChatMessage msg = byId.remove(id);
            if (msg != null) messages.remove(msg);
        }
    }

    public List<ChatMessage> getHistoryFor(UUID viewerId) {
        List<ChatMessage> result = new ArrayList<>();
        synchronized (messages) {
            for (ChatMessage msg : messages) {
                if (msg.wasSeenBy(viewerId)) {
                    result.add(msg);
                }
            }
        }
        return result;
    }

    public void cleanup() {
        synchronized (messages) {
            long cutoff = System.currentTimeMillis() - ttlMs;
            while (!messages.isEmpty() && messages.peekFirst().getTimestamp() < cutoff) {
                ChatMessage old = messages.pollFirst();
                if (old != null) byId.remove(old.getId());
            }
        }
    }

    public void reload(int maxMessages, int ttlSeconds) {
        this.maxMessages = maxMessages;
        this.ttlMs = ttlSeconds * 1000L;
    }
}