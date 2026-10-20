package ru.net.terracraft.chat.format;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslator;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class TranslationManager {

    private final JavaPlugin plugin;
    private final Map<Locale, Map<String, String>> translations = new HashMap<>();
    private MiniMessageTranslator currentTranslator;
    private int localeCount = 0;

    public TranslationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку плагина!");
            return;
        }

        saveDefaultIfMissing("messages_en.yml");
        saveDefaultIfMissing("messages_ru.yml");
        saveDefaultIfMissing("messages_de.yml");

        File langFolder = new File(dataFolder, "lang");
        File[] files = langFolder.listFiles((dir, name) ->
                name.startsWith("messages_") && name.endsWith(".yml"));

        if (files == null || files.length == 0) {
            plugin.getLogger().warning("Не найдено ни одного файла messages_*.yml!");
            return;
        }

        translations.clear();

        for (File file : files) {
            String fileName = file.getName();
            String localeCode = fileName.substring(
                    "messages_".length(),
                    fileName.length() - ".yml".length()
            );
            // pt_BR → pt-BR
            Locale locale = Locale.forLanguageTag(localeCode.replace('_', '-'));
            loadFile(file, locale);
        }

        if (currentTranslator != null) {
            GlobalTranslator.translator().removeSource(currentTranslator);
        }

        currentTranslator = new ChatTranslator(new HashMap<>(translations));
        GlobalTranslator.translator().addSource(currentTranslator);

        this.localeCount = files.length;
    }

    public void reload() {
        loadAll();
    }

    private void loadFile(File file, Locale locale) {
        Map<String, String> map = loadWithOverlay(file.getName());
        translations.put(locale, map);
        plugin.getLogger().info("Загружена локаль: " + locale
                + " (" + file.getName() + ", " + map.size() + " ключей)");
    }

    /**
     * Jar-база + overlay из файла игрока.
     * Новые ключи из jar всегда есть, старые — перезаписываются игроком.
     */
    private Map<String, String> loadWithOverlay(String fileName) {
        Map<String, String> result = new LinkedHashMap<>();

        // 1. База из jar
        try (var stream = plugin.getResource("lang/" + fileName)) {
            if (stream != null) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)
                );
                for (String key : yaml.getKeys(true)) {
                    if (!yaml.isString(key)) continue;
                    String value = yaml.getString(key);
                    if (value != null) result.put(key, value);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Не удалось загрузить jar-ресурс " + fileName + ": " + ex.getMessage());
        }

        // 2. Overlay из файла игрока
        File file = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(true)) {
                if (!yaml.isString(key)) continue;
                String value = yaml.getString(key);
                if (value != null) result.put(key, value);
            }
        }

        return result;
    }

    private void saveDefaultIfMissing(String fileName) {
        File file = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource("lang/" + fileName, false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ресурс не найден в jar: lang/" + fileName);
            }
        }
    }

    private static class ChatTranslator extends MiniMessageTranslator {

        private final Map<Locale, Map<String, String>> translations;

        private static final java.util.List<Locale> FALLBACKS = java.util.List.of(
                Locale.ENGLISH,
                Locale.forLanguageTag("ru")
        );

        ChatTranslator(Map<Locale, Map<String, String>> translations) {
            super(MiniMessage.miniMessage());
            this.translations = translations;
        }

        @Override
        public Key name() {
            return Key.key("terracraft", "chat");
        }

        @Override
        protected String getMiniMessageString(String key, Locale locale) {
            Map<String, String> map = translations.get(locale);
            if (map != null && map.containsKey(key)) return map.get(key);

            Locale langOnly = Locale.forLanguageTag(locale.getLanguage());
            map = translations.get(langOnly);
            if (map != null && map.containsKey(key)) return map.get(key);

            for (Locale fb : FALLBACKS) {
                Map<String, String> fallback = translations.get(fb);
                if (fallback != null && fallback.containsKey(key)) {
                    return fallback.get(key);
                }
            }

            return null;
        }
    }

    public void shutdown() {
        if (currentTranslator != null) {
            GlobalTranslator.translator().removeSource(currentTranslator);
            currentTranslator = null;
        }
    }

    public int getLocaleCount() { return localeCount; }
}