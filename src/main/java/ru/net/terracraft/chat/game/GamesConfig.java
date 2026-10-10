package ru.net.terracraft.chat.game;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GamesConfig {

    private final JavaPlugin plugin;
    private final List<GameDefinition> games = new ArrayList<>();

    public GamesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Загружает games.yml из папки плагина (или из ресурсов).
     */
    public void load() {
        games.clear();

        File file = new File(plugin.getDataFolder(), "games.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("games.yml", false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("games.yml не найден в jar!");
                return;
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection gamesSection = yaml.getConfigurationSection("games");
        if (gamesSection == null) {
            plugin.getLogger().warning("games.yml: секция 'games' отсутствует");
            return;
        }

        for (String id : gamesSection.getKeys(false)) {
            ConfigurationSection section = gamesSection.getConfigurationSection(id);
            if (section == null) continue;

            try {
                GameDefinition def = parseGame(id, section);
                if (def != null) games.add(def);
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка в игре '" + id + "': " + ex.getMessage());
            }
        }

        plugin.getLogger().info("Загружено игр: " + games.size());
    }

    private GameDefinition parseGame(String id, ConfigurationSection section) {
        String typeStr = section.getString("type", "unscramble").toUpperCase();
        GameType type;
        try {
            type = GameType.valueOf(typeStr);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Неизвестный тип игры '" + typeStr + "' в " + id);
            return null;
        }

        int weight = section.getInt("weight", 1);
        List<String> rewards = section.getStringList("rewards");

        List<String> words = section.getStringList("words");

        List<GameDefinition.Question> questions = new ArrayList<>();
        List<?> rawQuestions = section.getList("questions");
        if (rawQuestions != null) {
            for (Object obj : rawQuestions) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    String q = String.valueOf(map.get("question"));
                    List<String> answers = new ArrayList<>();
                    Object ansObj = map.get("answers");
                    if (ansObj instanceof List<?> ansList) {
                        for (Object a : ansList) answers.add(String.valueOf(a));
                    } else if (ansObj != null) {
                        answers.add(String.valueOf(ansObj));
                    }
                    boolean caseSensitive = Boolean.TRUE.equals(map.get("caseSensitive"));
                    questions.add(new GameDefinition.Question(q, answers, caseSensitive));
                }
            }
        }

        List<String> operations = section.getStringList("operations");
        int minNumber = section.getInt("minNumber", 1);
        int maxNumber = section.getInt("maxNumber", 100);

        return new GameDefinition(id, type, weight, rewards, words, questions,
                operations, minNumber, maxNumber);
    }

    public List<GameDefinition> getGames() {
        return games;
    }
}