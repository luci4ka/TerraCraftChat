package ru.net.terracraft.chat;

import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.config.ChatConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

public class DiscordWebhook {

    private final JavaPlugin plugin;
    private final ChatConfig config;
    private final HttpClient httpClient;

    private final Deque<String> buffer = new ArrayDeque<>();
    private int taskId = -1;

    public DiscordWebhook(JavaPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ===== Управление =====

    /**
     * Запускает задачу буферизации. Вызывать в onEnable().
     */
    public void start() {
        if (!config.discord().enabled() || config.discord().webhookUrl().isEmpty()) {
            return;
        }

        int interval = Math.max(1, config.discord().intervalSeconds()) * 20;

        taskId = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flush, interval, interval)
                .getTaskId();

        plugin.getLogger().info("Discord webhook запущен (интервал "
                + config.discord().intervalSeconds() + " сек).");
    }

    /**
     * Останавливает задачу и отправляет остаток буфера. Вызывать в onDisable().
     */
    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        flush();
    }

    // ===== Буферизация =====

    /**
     * Добавляет сообщение в буфер.
     *
     * @param player  ник игрока
     * @param world   мир
     * @param message сообщение (plain text)
     */
    public void queue(String player, String world, String message) {
        if (!config.discord().enabled() || config.discord().webhookUrl().isEmpty()) {
            return;
        }

        String formatted = config.discord().format()
                .replace("%player%", player)
                .replace("%world%", world)
                .replace("%message%", message);

        synchronized (buffer) {
            if (buffer.size() >= config.discord().maxBuffer()) {
                buffer.pollFirst();
            }
            buffer.addLast(formatted);
        }
    }

    /**
     * Отправляет всё содержимое буфера одной пачкой.
     * Discord поддерживает до 2000 символов на сообщение.
     */
    private void flush() {
        String payload;
        synchronized (buffer) {
            if (buffer.isEmpty()) return;

            StringBuilder sb = new StringBuilder();
            while (!buffer.isEmpty()) {
                String next = buffer.pollFirst();
                if (sb.length() + next.length() + 2 > 1900) {
                    buffer.addFirst(next);
                    break;
                }
                if (sb.length() > 0) sb.append("\n");
                sb.append(next);
            }
            payload = sb.toString();
        }

        if (payload.isEmpty()) return;

        send(payload);
    }

    // ===== Отправка =====

    private void send(String content) {
        try {
            String json = "{\"username\":\"" + escapeJson(config.discord().username())
                    + "\",\"content\":\"" + escapeJson(content) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.discord().webhookUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "TerraCraftChat/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        int code = response.statusCode();
                        if (code == 429) {
                            plugin.getLogger().warning("Discord rate limit! Сообщения буферизуются.");
                        } else if (code >= 400) {
                            plugin.getLogger().warning("Ошибка Discord webhook: HTTP " + code
                                    + " — " + response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Не удалось отправить в Discord: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка формирования webhook-запроса: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}