package ru.net.terracraft.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.config.ChatConfig;

import java.util.regex.Pattern;

public class LocalAnswerManager {

    private final JavaPlugin plugin;
    private final ChatConfig config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LocalAnswerManager(JavaPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Проверяет сообщение на совпадение с локальными ответами.
     * Если найдено — рассылает ответ всем.
     */
    public void checkAndRespond(String message) {
        ChatConfig.LocalAnswersSection answers = config.localAnswers();

        if (!answers.enabled() || answers.answers().isEmpty()) {
            return;
        }

        String lower = message.toLowerCase();

        for (var entry : answers.answers().entrySet()) {
            try {
                if (Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE)
                        .matcher(lower).find()) {

                    Component response = miniMessage.deserialize(
                            answers.prefix() + entry.getValue()
                    );
                    Bukkit.broadcast(response);
                    return; // один ответ на сообщение
                }
            } catch (Exception ex) {
                // некорректная регулярка — пропускаем
            }
        }
    }
}