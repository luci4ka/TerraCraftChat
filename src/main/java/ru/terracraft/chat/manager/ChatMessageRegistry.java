package ru.terracraft.chat.manager;

import ru.terracraft.chat.model.ChatMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кольцевой кэш последних сообщений (для кнопки удаления, тредов и перерисовки).
 *
 * <p>Раньше при регистрации нового сообщения выполнялся запрос
 * {@code getThreadReplies(id)} — но у только что созданного сообщения ответов
 * быть не может, запрос всегда возвращал пустой результат. Для восстановления
 * истории после рестарта есть {@link #registerWithoutThreadLoad} и batch-загрузка.
 */
public class ChatMessageRegistry {

    private volatile int maxMessages;
    private volatile long ttlMs;

    private final Deque<ChatMessage> messages = new ArrayDeque<>();
    private final Map<UUID, ChatMessage> byId = new ConcurrentHashMap<>();

    public ChatMessageRegistry(int maxMessages, int ttlSeconds) {
        this.maxMessages = maxMessages;
        this.ttlMs = ttlSeconds * 1000L;
    }

    /** Регистрирует сообщение. */
    public ChatMessage register(ChatMessage msg) {
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

        return msg;
    }

    /**
     * Регистрирует сообщение без обращения к БД.
     * Используется при восстановлении истории при старте —
     * треды загружаются отдельно (batch).
     */
    public ChatMessage registerWithoutThreadLoad(ChatMessage msg) {
        return register(msg);
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

    public int size() {
        synchronized (messages) {
            return messages.size();
        }
    }
}
