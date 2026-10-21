package ru.terracraft.chat.integration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.terracraft.chat.config.ChatConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class LocalAnswerManager {

    private final JavaPlugin plugin;
    private ChatConfig config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // LinkedHashMap — детерминированный порядок
    private final Map<Pattern, String> compiledAnswers = new LinkedHashMap<>();

    // Кулдаун: последний ответ бота (global)
    private final AtomicLong lastAnswerAt = new AtomicLong(0);

    public LocalAnswerManager(JavaPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
        compile();
    }

    public void reload(ChatConfig newConfig) {
        this.config = newConfig;
        compile();
    }

    public void compile() {
        compiledAnswers.clear();

        if (!config.localAnswers().enabled()) return;

        for (var entry : config.localAnswers().answers().entrySet()) {
            try {
                compiledAnswers.put(
                        Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                        entry.getValue()
                );
            } catch (Exception ex) {
                plugin.getLogger().warning("Некорректная регулярка в localAnswers: " + entry.getKey());
            }
        }
    }

    public void checkAndRespond(String message) {
        if (compiledAnswers.isEmpty()) return;

        // Кулдаун: не чаще раза в N секунд
        int cooldown = config.localAnswers().cooldownSeconds();
        long now = System.currentTimeMillis();
        long last = lastAnswerAt.get();
        if (cooldown > 0 && (now - last) < cooldown * 1000L) return;

        String lower = message.toLowerCase(Locale.ROOT);

        for (var entry : compiledAnswers.entrySet()) {
            if (entry.getKey().matcher(lower).find()) {
                Component response = miniMessage.deserialize(
                        config.localAnswers().prefix() + entry.getValue()
                );
                Bukkit.broadcast(response);
                lastAnswerAt.set(now);
                return;
            }
        }
    }
}