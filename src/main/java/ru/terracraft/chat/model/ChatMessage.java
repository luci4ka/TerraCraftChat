package ru.terracraft.chat.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatMessage {

    private final UUID id;
    private final UUID senderUuid;
    private final String senderName;
    private final String rawMessage;
    private final String channelId;
    private final long timestamp;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    private final List<ThreadReply> thread = new CopyOnWriteArrayList<>();

    public ChatMessage(UUID senderUuid, String senderName, String rawMessage, String channelId) {
        this(UUID.randomUUID(), senderUuid, senderName, rawMessage, channelId);
    }

    public ChatMessage(UUID id, UUID senderUuid, String senderName, String rawMessage, String channelId) {
        this.id = id;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.rawMessage = rawMessage;
        this.channelId = channelId;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getId() { return id; }
    public UUID getSenderUuid() { return senderUuid; }
    public String getSenderName() { return senderName; }
    public String getRawMessage() { return rawMessage; }
    public String getChannelId() { return channelId; }
    public long getTimestamp() { return timestamp; }
    public Set<UUID> getViewers() { return Set.copyOf(viewers); }

    public void addViewer(UUID uuid) { viewers.add(uuid); }
    public boolean wasSeenBy(UUID uuid) { return viewers.contains(uuid); }

    public void addReply(ThreadReply reply) { thread.add(reply); }
    public int getThreadSize() { return thread.size(); }

    /** Иммутабельный снимок тред-ответов. */
    public List<ThreadReply> getThread() {
        return List.copyOf(thread);
    }

    public List<ThreadReply> getRecentReplies(int n) {
        int size = thread.size();
        if (size <= n) return List.copyOf(thread);
        return new ArrayList<>(thread.subList(size - n, size));
    }

    /**
     * Ответ в треде.
     *
     * @param id         id ответа (для правки/удаления)
     * @param authorId   автор
     * @param authorName имя автора на момент отправки
     * @param text       текст ответа
     * @param timestamp  время отправки (мс)
     */
    public record ThreadReply(UUID id, UUID authorId, String authorName,
                              String text, long timestamp) {

        public ThreadReply(UUID authorId, String authorName, String text) {
            this(UUID.randomUUID(), authorId, authorName, text, System.currentTimeMillis());
        }
    }
}
