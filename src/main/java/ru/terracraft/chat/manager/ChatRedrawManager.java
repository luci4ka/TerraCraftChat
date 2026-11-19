package ru.terracraft.chat.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.format.ChatFormatter;
import ru.terracraft.chat.model.Channel;
import ru.terracraft.chat.model.ChatMessage;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Перерисовка чата после удаления сообщения (приём «сдвига строк», как в Allium).
 */
public class ChatRedrawManager {

    private static final int BATCH_SIZE = 20;

    private final TerraCraftChatPlugin plugin;
    private final ChatFormatter formatter;

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

        ChannelManager channels = plugin.getChannelManager();

        for (ChatMessage message : history) {
            if (message.getId().equals(removedId)) continue;

            Player sender = Bukkit.getPlayer(message.getSenderUuid());
            String senderName = sender != null ? sender.getName() : message.getSenderName();

            Channel channel = message.getChannelId() != null
                    ? channels.get(message.getChannelId())
                    : null;
            if (channel == null) {
                channel = channels.getDefault();
            }

            // Сообщение канала, к которому у зрителя больше нет доступа,
            // перерисовывать не нужно
            if (channel != null && !channels.canUse(viewer, channel)) continue;

            Component formatted;
            if (channel != null && sender != null) {
                formatted = formatter.formatChannel(
                        sender,
                        message.getRawMessage(),
                        viewer,
                        formatter.envelope(sender, channel.formatKey()),
                        message.getId()
                );
            } else {
                String prefix = channel != null
                        ? "[" + channel.id().toUpperCase(Locale.ROOT) + "] "
                        : "";
                formatted = Component.text(prefix + senderName + ": " + message.getRawMessage());
            }

            viewer.sendMessage(formatted);

            // === Ответы в треде ===
            for (ChatMessage.ThreadReply reply : message.getThread()) {
                if (plugin.getIgnoreManager().isIgnoring(viewer.getUniqueId(), reply.authorId())) {
                    continue;
                }
                viewer.sendMessage(formatter.formatThreadReply(message, reply));
            }
        }
    }
}
