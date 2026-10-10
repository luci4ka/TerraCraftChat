package ru.net.terracraft.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatMessage {

    private final UUID id;
    private final String senderName;
    private final String rawMessage;
    private final long timestamp;
    private final Set<UUID> viewers = new HashSet<>();

    // Тред
    private final List<ThreadReply> thread = new CopyOnWriteArrayList<>();

    public ChatMessage(String senderName, String rawMessage) {
        this.id = UUID.randomUUID();
        this.senderName = senderName;
        this.rawMessage = rawMessage;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getId() { return id; }
    public String getSenderName() { return senderName; }
    public String getRawMessage() { return rawMessage; }
    public long getTimestamp() { return timestamp; }
    public Set<UUID> getViewers() { return viewers; }

    public void addViewer(UUID uuid) { viewers.add(uuid); }
    public boolean wasSeenBy(UUID uuid) { return viewers.contains(uuid); }

    // ===== Тред =====

    public void addReply(ThreadReply reply) { thread.add(reply); }
    public List<ThreadReply> getThread() { return thread; }
    public int getThreadSize() { return thread.size(); }

    public List<ThreadReply> getRecentReplies(int n) {
        int size = thread.size();
        if (size <= n) return new ArrayList<>(thread);
        return new ArrayList<>(thread.subList(size - n, size));
    }

    // ===== Вложенный класс =====

    public static class ThreadReply {
        private final UUID authorId;
        private final String authorName;
        private final String text;
        private final long timestamp;

        public ThreadReply(UUID authorId, String authorName, String text) {
            this.authorId = authorId;
            this.authorName = authorName;
            this.text = text;
            this.timestamp = System.currentTimeMillis();
        }

        public UUID getAuthorId() { return authorId; }
        public String getAuthorName() { return authorName; }
        public String getText() { return text; }
        public long getTimestamp() { return timestamp; }
    }
}