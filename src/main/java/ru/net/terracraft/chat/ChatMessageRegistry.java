package ru.net.terracraft.chat;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ChatMessageRegistry {

    private final JavaPlugin plugin;
    private final int maxMessages;
    private final long ttlMs;

    private final Deque<ChatMessage> messages = new ArrayDeque<>();
    private final Map<UUID, ChatMessage> byId = new HashMap<>();

    public ChatMessageRegistry(JavaPlugin plugin, int maxMessages, int ttlSeconds) {
        this.plugin = plugin;
        this.maxMessages = maxMessages;
        this.ttlMs = ttlSeconds * 1000L;
    }

    public ChatMessage register(String sender, String raw) {
        ChatMessage msg = new ChatMessage(sender, raw);

        synchronized (messages) {
            messages.addLast(msg);
            byId.put(msg.getId(), msg);

            // Чистим по количеству
            while (messages.size() > maxMessages) {
                ChatMessage old = messages.pollFirst();
                if (old != null) byId.remove(old.getId());
            }

            // Чистим по TTL
            long cutoff = System.currentTimeMillis() - ttlMs;
            while (!messages.isEmpty() && messages.peekFirst().getTimestamp() < cutoff) {
                ChatMessage old = messages.pollFirst();
                if (old != null) byId.remove(old.getId());
            }
        }

        return msg;
    }

    public Optional<ChatMessage> get(UUID id) {
        synchronized (messages) {
            return Optional.ofNullable(byId.get(id));
        }
    }

    public void remove(UUID id) {
        synchronized (messages) {
            ChatMessage msg = byId.remove(id);
            if (msg != null) messages.remove(msg);
        }
    }
}