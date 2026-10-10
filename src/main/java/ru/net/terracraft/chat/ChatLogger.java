package ru.net.terracraft.chat;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatLogger {

    private final JavaPlugin plugin;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BufferedWriter writer;
    private File logFile;

    public ChatLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Открывает файл лога. Вызывать в onEnable().
     */
    public void open() {
        try {
            File logsFolder = new File(plugin.getDataFolder(), "logs");
            if (!logsFolder.exists() && !logsFolder.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку logs!");
                return;
            }

            // Один файл на день — chat-2026-02-10.log
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            this.logFile = new File(logsFolder, "chat-" + date + ".log");

            // append = true, чтобы при рестарте не терять старые записи
            this.writer = new BufferedWriter(new FileWriter(logFile, true));

        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось открыть лог чата: " + e.getMessage());
        }
    }

    /**
     * Записывает сообщение в лог.
     *
     * @param type     тип: LOCAL, GLOBAL, MSG, REPLY
     * @param sender   отправитель
     * @param receiver получатель (для ЛС), может быть null
     * @param message  текст сообщения
     */
    public void log(String type, String sender, String receiver, String message) {
        if (writer == null) {
            return;
        }

        String timestamp = LocalDateTime.now().format(formatter);
        String line;

        if (receiver != null) {
            line = "[" + timestamp + "] [" + type + "] " + sender + " -> " + receiver + ": " + message;
        } else {
            line = "[" + timestamp + "] [" + type + "] " + sender + ": " + message;
        }

        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка записи в лог чата: " + e.getMessage());
        }
    }

    /**
     * Закрывает файл. Вызывать в onDisable().
     */
    public void close() {
        if (writer == null) {
            return;
        }

        try {
            writer.close();
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка закрытия лога чата: " + e.getMessage());
        } finally {
            writer = null;
        }
    }
}