package ru.terracraft.chat.moderation;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.terracraft.chat.TerraCraftChatPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class RuleManager {

    private final JavaPlugin plugin;
    private final List<Rule> rules = new ArrayList<>();

    public RuleManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rules.clear();

        File file = new File(plugin.getDataFolder(), "rules.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("rules.yml", false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("rules.yml не найден в jar!");
                return;
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("rules");
        if (section == null) {
            plugin.getLogger().warning("rules.yml: секция 'rules' отсутствует");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) continue;

            String patternStr = r.getString("pattern", "");
            String command = r.getString("command", "");
            String reason = r.getString("reason", "");
            boolean cancel = r.getBoolean("cancel-message", true);

            if (patternStr.isEmpty() || command.isEmpty()) {
                plugin.getLogger().warning("rules.yml: правило '" + id + "' без pattern или command");
                continue;
            }

            try {
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                rules.add(new Rule(id, pattern, command, reason, cancel));
            } catch (Exception ex) {
                plugin.getLogger().warning("rules.yml: некорректный regex в '" + id + "': " + ex.getMessage());
            }
        }

        plugin.getLogger().info("Загружено правил: " + rules.size());
    }

    public void reload() {
        load();
    }

    /**
     * Проверяет сообщение по правилам.
     * Возвращает сработавшее правило или null.
     */
    public Rule match(String message) {
        if (message == null || message.isEmpty()) return null;
        String lower = message.toLowerCase(Locale.ROOT);
        for (Rule rule : rules) {
            if (rule.pattern().matcher(lower).find()) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Выполняет действие правила для игрока.
     * ВАЖНО: вызывать только из main-потока.
     */
    public void execute(Rule rule, Player player) {
        String command = rule.buildCommand(player.getName());
        if (command.startsWith("/")) command = command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        plugin.getLogger().info("[Rule] " + rule.id() + " -> " + player.getName());

        // Аудит
        var storage = ((TerraCraftChatPlugin) plugin).getStorageManager();
        if (storage != null) {
            storage.logRule(rule.id(), player.getUniqueId(), player.getName(),
                    rule.reason(), System.currentTimeMillis());
        }
    }

    public int size() {
        return rules.size();
    }
}