package ru.net.terracraft.chat.integration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
    private volatile ChatConfig config;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final Deque<String> buffer = new ArrayDeque<>();
    private int taskId = -1;

    public DiscordWebhook(JavaPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void start() {
        ChatConfig cfg = this.config;
        if (!cfg.discord().enabled() || cfg.discord().webhookUrl().isEmpty()) return;

        int interval = Math.max(1, cfg.discord().intervalSeconds()) * 20;
        taskId = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flush, interval, interval)
                .getTaskId();

        plugin.getLogger().info("Discord webhook запущен (интервал "
                + cfg.discord().intervalSeconds() + " сек).");
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        // Финальный flush — асинхронно, чтобы не блокировать main
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::flush);
    }

    public void queue(String player, String world, String message) {
        ChatConfig cfg = this.config;
        if (!cfg.discord().enabled() || cfg.discord().webhookUrl().isEmpty()) return;

        String formatted = cfg.discord().format()
                .replace("%player%", player)
                .replace("%world%", world)
                .replace("%message%", message);

        synchronized (buffer) {
            if (buffer.size() >= cfg.discord().maxBuffer()) buffer.pollFirst();
            buffer.addLast(formatted);
        }
    }

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

    private void send(String content) {
        ChatConfig cfg = this.config;
        try {
            JsonObject body = new JsonObject();
            body.addProperty("username", cfg.discord().username());
            body.addProperty("content", content);

            // === Ключевое: не парсим @everyone/@here/роли ===
            JsonObject allowedMentions = new JsonObject();
            allowedMentions.add("parse", new JsonArray()); // пустой массив
            body.add("allowed_mentions", allowedMentions);

            String json = gson.toJson(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.discord().webhookUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "TerraCraftChat/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        int code = response.statusCode();

                        if (code == 429) {
                            long retryAfterMs = parseRetryAfter(response);
                            plugin.getLogger().warning("Discord rate limit! "
                                    + "Retry after " + retryAfterMs + " ms. "
                                    + "Сообщение возвращено в буфер.");

                            // Возвращаем ОДНОЙ строкой — не разбиваем по \n,
                            // иначе формат ломается.
                            synchronized (buffer) {
                                if (buffer.size() >= cfg.discord().maxBuffer()) {
                                    buffer.pollFirst();
                                }
                                buffer.addFirst(content);
                            }

                            if (retryAfterMs > 0) {
                                plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                                        plugin, this::flush,
                                        Math.max(20, retryAfterMs / 50)
                                );
                            }
                            return;
                        }

                        if (code >= 400) {
                            plugin.getLogger().warning("Discord webhook: HTTP " + code
                                    + " — " + response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Discord send failed: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            plugin.getLogger().warning("Webhook build error: " + e.getMessage());
        }
    }

    /**
     * Парсит Retry-After из заголовков (секунды) или из JSON (retry_after, ms).
     */
    private long parseRetryAfter(HttpResponse<String> response) {
        var header = response.headers().firstValue("Retry-After");
        if (header.isPresent()) {
            try {
                return (long) (Double.parseDouble(header.get()) * 1000);
            } catch (NumberFormatException ignored) {}
        }
        try {
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (json != null && json.has("retry_after")) {
                return (long) (json.get("retry_after").getAsDouble() * 1000);
            }
        } catch (Exception ignored) {}
        return 1000;
    }

    public void reload(ChatConfig newConfig) {
        this.config = newConfig;
        stop();
        start();
    }
}