package ru.net.terracraft.chat.model;

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
    public List<ThreadReply> getThread() { return thread; }
    public int getThreadSize() { return thread.size(); }

    public List<ThreadReply> getRecentReplies(int n) {
        int size = thread.size();
        if (size <= n) return new ArrayList<>(thread);
        return new ArrayList<>(thread.subList(size - n, size));
    }

    public static class ThreadReply {
        private final UUID id;
        private final UUID authorId;
        private final String authorName;
        private final String text;
        private final long timestamp;

        public ThreadReply(UUID authorId, String authorName, String text) {
            this(UUID.randomUUID(), authorId, authorName, text, System.currentTimeMillis());
        }

        public ThreadReply(UUID id, UUID authorId, String authorName, String text, long timestamp) {
            this.id = id;
            this.authorId = authorId;
            this.authorName = authorName;
            this.text = text;
            this.timestamp = timestamp;
        }

        public UUID getId() { return id; }
        public UUID getAuthorId() { return authorId; }
        public String getAuthorName() { return authorName; }
        public String getText() { return text; }
        public long getTimestamp() { return timestamp; }
    }
}