package ru.terracraft.chat.integration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.config.ChatConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AutoBroadcast {

    private static final String KEY_PREFIX = "@key:";

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

    public void start() {
        List<String> messages = config.autobroadcast().messages();
        if (messages.isEmpty()) return;

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
        Component message = parseMessage(raw);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }

        if (plugin instanceof TerraCraftChatPlugin tcc && tcc.getChatLogger() != null) {
            tcc.getChatLogger().log("AUTOBROADCAST", "CONSOLE", null, raw);
        }
    }

    /**
     * Разбор строки:
     *  - "@key:xxx"    → Component.translatable("xxx")
     *  - "<gold>xxx"   → MiniMessage
     *  - "&6xxx"       → legacy &-codes
     *
     * Старые конфиги (без @key:) — fallback на эвристику:
     *   содержит "." и нет пробелов → ключ перевода.
     */
    private Component parseMessage(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();

        // 1. Явный префикс
        if (raw.startsWith(KEY_PREFIX)) {
            String key = raw.substring(KEY_PREFIX.length()).trim();
            return Component.translatable(key);
        }

        // 2. Legacy fallback (обратная совместимость)
        if (looksLikeTranslationKey(raw)) {
            return Component.translatable(raw);
        }

        // 3. MiniMessage → legacy
        try {
            return miniMessage.deserialize(raw);
        } catch (Exception ex) {
            return legacySerializer.deserialize(raw);
        }
    }

    private boolean looksLikeTranslationKey(String raw) {
        return raw.contains(".")
                && !raw.contains(" ")
                && !raw.contains("<")
                && !raw.contains("&");
    }
}