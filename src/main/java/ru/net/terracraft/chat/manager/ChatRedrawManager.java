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

    // Сколько строк "сдвинуть" при очистке (как в Allium)
    private static final int CLEAR_LINES = 120;
    // Строк в одном пакете (клиент обработает как N строк)
    private static final int BATCH_SIZE = 20;

    public ChatRedrawManager(TerraCraftChatPlugin plugin, ChatFormatter formatter) {
        this.plugin = plugin;
        this.formatter = formatter;
    }

    /**
     * Перерисовывает чат зрителя без указанного сообщения.
     * Работает для всех: онлайн + офлайн.
     * Очистка и перерисовка — в одном тике (без "мигания").
     */
    public void redrawFor(Player viewer, UUID removedId) {
        if (viewer == null || !viewer.isOnline()) return;

        // 1. Очистка — все пакеты сразу, в одном тике
        int batches = (CLEAR_LINES + BATCH_SIZE - 1) / BATCH_SIZE;
        Component emptyBatch = createEmptyBatch();
        for (int i = 0; i < batches; i++) {
            viewer.sendMessage(emptyBatch);
        }

        // 2. Перерисовка — сразу же, в этом же тике
        redrawHistory(viewer, removedId);
    }

    /**
     * Component с BATCH_SIZE переносами строк.
     * Клиент отрисует как BATCH_SIZE пустых строк.
     */
    private Component createEmptyBatch() {
        return Component.text("\n".repeat(BATCH_SIZE));
    }

    /**
     * Пересобирает историю без удалённого сообщения.
     */
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
                    // Офлайн-отправитель — упрощённый формат
                    formatted = Component.text("[" + channel.id().toUpperCase(Locale.ROOT) + "] ")
                            .append(Component.text(senderName + ": " + m.getRawMessage()));
                }
            } else {
                // Канал не найден — без префикса
                formatted = Component.text(senderName + ": " + m.getRawMessage());
            }

            viewer.sendMessage(formatted);

            // Ответы в треде — перерисовываем
            if (!m.getThread().isEmpty()) {
                for (ChatMessage.ThreadReply reply : m.getThread()) {
                    Component replyComp = formatter.formatThreadReply(m, reply);
                    viewer.sendMessage(replyComp);
                }
            }
        }
    }
}