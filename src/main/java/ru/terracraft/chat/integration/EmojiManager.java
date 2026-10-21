package ru.terracraft.chat.integration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiManager {

    // Маркеры для эмодзи. \u0005код\u0006
    public static final String EMOJI_MARKER_START = "\u0005";
    public static final String EMOJI_MARKER_END = "\u0006";

    private final JavaPlugin plugin;
    private final Map<String, String> emojis = new HashMap<>();

    private static final Pattern EMOJI_PATTERN = Pattern.compile(":([a-z0-9_]+):");
    private static final Pattern EMOJI_MARKER_PATTERN = Pattern.compile(
            "\u0005([a-z0-9_]+)\u0006"
    );

    public EmojiManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Загружает emojis.yml из папки плагина (или из ресурсов, если файла нет).
     */
    public void load() {
        emojis.clear();

        File file = new File(plugin.getDataFolder(), "emojis.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("emojis.yml", false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("emojis.yml не найден в jar!");
                return;
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String value = yaml.getString(key);
            if (value != null) {
                emojis.put(key.toLowerCase(java.util.Locale.ROOT), value);
            }
        }

        plugin.getLogger().info("Загружено эмодзи: " + emojis.size());
    }

    /**
     * Проверяет, есть ли такое эмодзи.
     */
    public boolean has(String code) {
        return emojis.containsKey(code.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Возвращает MiniMessage-строку эмодзи или null.
     */
    public String getRaw(String code) {
        return emojis.get(code.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Заменяет все :код: на маркеры \u0005код\u0006.
     * Используется ДО парсинга сообщения. После парсинга
     * ChatFormatter вызывает replaceEmojiMarkers, чтобы превратить
     * маркеры в Component через MiniMessage.
     */
    public String replaceWithMarkers(String message) {
        if (emojis.isEmpty()) {
            return message;
        }

        Matcher matcher = EMOJI_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            if (emojis.containsKey(code)) {
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(EMOJI_MARKER_START + code + EMOJI_MARKER_END));
            } else {
                // Неизвестный код — оставляем как есть
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public static Pattern getMarkerPattern() {
        return EMOJI_MARKER_PATTERN;
    }

    public int size() {
        return emojis.size();
    }
}