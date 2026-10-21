package ru.net.terracraft.chat.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.format.ChatFormatter;
import ru.net.terracraft.chat.model.Channel;
import ru.net.terracraft.chat.model.ChatMessage;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatRedrawManager {

    private final TerraCraftChatPlugin plugin;
    private final ChatFormatter formatter;

    private static final int BATCH_SIZE = 20;

    public ChatRedrawManager(TerraCraftChatPlugin plugin, ChatFormatter formatter) {
        this.plugin = plugin;
        this.formatter = formatter;
    }

    public void redrawFor(Player viewer, UUID removedId) {
        if (viewer == null || !viewer.isOnline()) return;

        int clearLines = Math.max(0, plugin.getChatConfig().delete().clearLines());
        int batches = (clearLines + BATCH_SIZE - 1) / BATCH_SIZE;

        if (batches > 0) {
            Component emptyBatch = createEmptyBatch();
            for (int i = 0; i < batches; i++) {
                viewer.sendMessage(emptyBatch);
            }
        }

        redrawHistory(viewer, removedId);
    }

    private Component createEmptyBatch() {
        return Component.text("\n".repeat(BATCH_SIZE));
    }

    private void redrawHistory(Player viewer, UUID removedId) {
        List<ChatMessage> history = plugin.getChatMessageRegistry()
                .getHistoryFor(viewer.getUniqueId());

        for (ChatMessage m : history) {
            if (m.getId().equals(removedId)) continue;

            Player sender = Bukkit.getPlayer(m.getSenderUuid());
            String senderName = sender != null ? sender.getName() : m.getSenderName();

            Channel channel = null;
            if (m.getChannelId() != null) {
                channel = plugin.getChannelManager().get(m.getChannelId());
            }
            if (channel == null) {
                channel = plugin.getChannelManager().getDefault();
            }

            Component formatted;
            if (channel != null) {
                String format = plugin.getChatConfig().formats().byKey(channel.formatKey());
                if (sender != null) {
                    formatted = formatter.formatChannel(
                            sender, m.getRawMessage(), viewer, format, m.getId());
                } else {
                    formatted = Component.text("[" + channel.id().toUpperCase(Locale.ROOT) + "] ")
                            .append(Component.text(senderName + ": " + m.getRawMessage()));
                }
            } else {
                formatted = Component.text(senderName + ": " + m.getRawMessage());
            }

            viewer.sendMessage(formatted);

            // === Thread replies ===
            List<ChatMessage.ThreadReply> thread = m.getThread();
            if (!thread.isEmpty()) {
                for (ChatMessage.ThreadReply reply : thread) {
                    // Пропускаем replies, если отправитель игнорируется viewer'ом
                    if (plugin.getIgnoreManager().isIgnoring(viewer.getUniqueId(), reply.getAuthorId())) {
                        continue;
                    }
                    Component replyComp = formatter.formatThreadReply(m, reply);
                    viewer.sendMessage(replyComp);
                }
            }
        }
    }
}