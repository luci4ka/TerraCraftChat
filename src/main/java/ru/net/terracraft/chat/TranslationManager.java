package ru.net.terracraft.chat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslator;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
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
            Locale locale = Locale.forLanguageTag(localeCode);
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
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, String> map = new HashMap<>();

        for (String key : yaml.getKeys(true)) {
            if (!yaml.isString(key)) continue;
            String value = yaml.getString(key);
            if (value != null) {
                map.put(key, value);
            }
        }

        translations.put(locale, map);
        plugin.getLogger().info("Загружена локаль: " + locale
                + " (" + file.getName() + ", " + map.size() + " ключей)");
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
            // 1. Точное совпадение (ru_ru)
            Map<String, String> map = translations.get(locale);
            if (map != null && map.containsKey(key)) {
                return map.get(key);
            }

            // 2. Языковая часть (ru_ru → ru)
            Locale langOnly = Locale.forLanguageTag(locale.getLanguage());
            map = translations.get(langOnly);
            if (map != null && map.containsKey(key)) {
                return map.get(key);
            }

            // 3. Fallback на английский
            Map<String, String> fallback = translations.get(Locale.ENGLISH);
            if (fallback != null && fallback.containsKey(key)) {
                return fallback.get(key);
            }

            // 4. Ничего не нашли — Adventure покажет сам ключ
            return null;
        }
    }

    public int getLocaleCount() { return localeCount; }
}