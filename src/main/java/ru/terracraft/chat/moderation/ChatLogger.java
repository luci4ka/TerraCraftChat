package ru.terracraft.chat.moderation;

import ru.terracraft.chat.TerraCraftChatPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatLogger {

    private final TerraCraftChatPlugin plugin;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    private volatile BufferedWriter writer;
    private volatile LocalDate currentDate;

    public ChatLogger(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        try {
            File logsFolder = new File(plugin.getDataFolder(), "logs");
            if (!logsFolder.exists() && !logsFolder.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку logs!");
                return;
            }

            this.currentDate = LocalDate.now();
            File logFile = new File(logsFolder, "chat-" + currentDate.format(dateFormatter) + ".log");
            this.writer = new BufferedWriter(new FileWriter(logFile, true));

            plugin.getServer().getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flush, 100L, 100L);

        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось открыть лог чата: " + e.getMessage());
        }
    }

    /**
     * Запись в очередь (не блокирует).
     */
    public void log(String type, String sender, String receiver, String message) {
        if (writer == null) return;

        // Пропускаем личные, если отключено
        if (receiver != null && !plugin.getChatConfig().logging().logPrivate()) {
            return;
        }

        // Санитизация: \n и \r в тексте позволяют подделать строки лога
        String safeMessage = message
                .replace("\r", "\\r")
                .replace("\n", "\\n");

        String timestamp = LocalDateTime.now().format(formatter);
        String line;
        if (receiver != null) {
            line = "[" + timestamp + "] [" + type + "] " + sender + " -> " + receiver + ": " + safeMessage;
        } else {
            line = "[" + timestamp + "] [" + type + "] " + sender + ": " + safeMessage;
        }

        queue.add(line);

        // Быстрый flush, если очередь разрастается
        if (queue.size() > 100) {
            flush();
        }
    }

    private void flush() {
        // Локальная копия: writer может стать null в close() параллельно
        BufferedWriter w = this.writer;
        if (w == null || queue.isEmpty()) return;
        if (!flushing.compareAndSet(false, true)) return;

        try {
            LocalDate today = LocalDate.now();
            if (!today.equals(currentDate)) {
                w = rotate(today);
            }

            String line;
            while ((line = queue.poll()) != null) {
                w.write(line);
                w.newLine();
            }
            w.flush();

        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка записи в лог: " + e.getMessage());
        } finally {
            flushing.set(false);
        }
    }

    private BufferedWriter rotate(LocalDate newDate) throws IOException {
        BufferedWriter old = this.writer;
        if (old != null) {
            try { old.close(); } catch (IOException ignored) {}
        }
        this.currentDate = newDate;
        File logFile = new File(
                new File(plugin.getDataFolder(), "logs"),
                "chat-" + newDate.format(dateFormatter) + ".log"
        );
        BufferedWriter fresh = new BufferedWriter(new FileWriter(logFile, true));
        this.writer = fresh;
        plugin.getLogger().info("Chat log rotated to " + logFile.getName());
        return fresh;
    }

    public void close() {
        // Последний flush
        flush();

        BufferedWriter w = this.writer;
        if (w == null) return;
        try {
            w.close();
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка закрытия лога: " + e.getMessage());
        } finally {
            this.writer = null;
        }
    }
}