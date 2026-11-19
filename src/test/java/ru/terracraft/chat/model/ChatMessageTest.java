package ru.terracraft.chat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChatMessage — зрители и тред")
class ChatMessageTest {

    private static ChatMessage message() {
        return new ChatMessage(UUID.randomUUID(), "Steve", "привет", "local");
    }

    @Test
    void tracksViewers() {
        ChatMessage message = message();
        UUID viewer = UUID.randomUUID();

        assertFalse(message.wasSeenBy(viewer));
        message.addViewer(viewer);

        assertTrue(message.wasSeenBy(viewer));
        assertTrue(message.getViewers().contains(viewer));
    }

    @Test
    @DisplayName("getViewers отдаёт копию — изменить состояние снаружи нельзя")
    void viewersSnapshotIsImmutable() {
        ChatMessage message = message();
        message.addViewer(UUID.randomUUID());

        assertThrows(UnsupportedOperationException.class,
                () -> message.getViewers().clear());
    }

    @Test
    void tracksThreadReplies() {
        ChatMessage message = message();

        message.addReply(new ChatMessage.ThreadReply(UUID.randomUUID(), "Alex", "первый"));
        message.addReply(new ChatMessage.ThreadReply(UUID.randomUUID(), "Bob", "второй"));

        assertEquals(2, message.getThreadSize());
        assertEquals(List.of("первый", "второй"),
                message.getThread().stream().map(ChatMessage.ThreadReply::text).toList());
    }

    @Test
    @DisplayName("getRecentReplies отдаёт последние N ответов в исходном порядке")
    void recentRepliesAreTrimmed() {
        ChatMessage message = message();
        for (int i = 1; i <= 5; i++) {
            message.addReply(new ChatMessage.ThreadReply(UUID.randomUUID(), "Player" + i, "reply" + i));
        }

        assertEquals(List.of("reply3", "reply4", "reply5"),
                message.getRecentReplies(3).stream().map(ChatMessage.ThreadReply::text).toList());
        assertEquals(5, message.getRecentReplies(10).size());
    }

    @Test
    @DisplayName("getThread отдаёт иммутабельный снимок")
    void threadSnapshotIsImmutable() {
        ChatMessage message = message();
        message.addReply(new ChatMessage.ThreadReply(UUID.randomUUID(), "Alex", "привет"));

        List<ChatMessage.ThreadReply> snapshot = message.getThread();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    @DisplayName("ThreadReply — record с автогенерацией id и времени")
    void threadReplyAutoFillsIdAndTimestamp() {
        long before = System.currentTimeMillis();
        ChatMessage.ThreadReply reply = new ChatMessage.ThreadReply(UUID.randomUUID(), "Alex", "привет");

        assertTrue(reply.id() != null);
        assertTrue(reply.timestamp() >= before);
        assertEquals("привет", reply.text());
    }
}
