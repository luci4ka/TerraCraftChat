package ru.net.terracraft.chat.integration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.config.ChatConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AutoBroadcast {

    private final JavaPlugin plugin;
    private ChatConfig config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacyAmpersand().toBuilder()
                    .hexColors()
                    .build();

    private final Random random = new Random();
    private final List<String> queue = new ArrayList<>();
    private int taskId = -1;

    public AutoBroadcast(JavaPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ===== Управление =====

    public void start() {
        List<String> messages = config.autobroadcast().messages();
        if (messages.isEmpty()) {
            return;
        }

        rebuildQueue();

        int intervalTicks = Math.max(1, config.autobroadcast().interval()) * 20;

        taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::broadcastNext, intervalTicks, intervalTicks)
                .getTaskId();

        plugin.getLogger().info("AutoBroadcast запущен (" + messages.size()
                + " сообщений, интервал " + config.autobroadcast().interval() + " сек).");
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void reload(ChatConfig newConfig) {
        this.config = newConfig;
        stop();
        start();
    }

    // ===== Внутреннее =====

    private void rebuildQueue() {
        queue.clear();
        queue.addAll(config.autobroadcast().messages());

        if (config.autobroadcast().random()) {
            Collections.shuffle(queue, random);
        }
    }

    private void broadcastNext() {
        if (queue.isEmpty()) {
            rebuildQueue();
            if (queue.isEmpty()) return;
        }

        String raw = queue.remove(0);

        Component message;

        // Если строка — ключ перевода (содержит точку и не содержит пробелов/MiniMessage-тегов)
        if (isTranslationKey(raw)) {
            message = Component.translatable(raw);
        } else {
            // Fallback: MiniMessage или legacy
            try {
                message = miniMessage.deserialize(raw);
            } catch (Exception ex) {
                message = legacySerializer.deserialize(raw);
            }
        }

        // Отправляем каждому игроку отдельно, чтобы перевод подставился по локали клиента
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }

        // Логирование
        if (plugin instanceof TerraCraftChatPlugin tcc && tcc.getChatLogger() != null) {
            tcc.getChatLogger().log("AUTOBROADCAST", "CONSOLE", null, raw);
        }
    }

    /**
     * Проверяет, является ли строка ключом перевода.
     * Ключ содержит точку, не содержит пробелов и MiniMessage-тегов.
     */
    private boolean isTranslationKey(String raw) {
        return raw != null
                && raw.contains(".")
                && !raw.contains(" ")
                && !raw.contains("<")
                && !raw.contains("&");
    }
}