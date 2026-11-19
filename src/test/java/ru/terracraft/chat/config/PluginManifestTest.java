package ru.terracraft.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Валидация {@code paper-plugin.yml}: манифест ломается молча (плагин просто
 * не загрузится или потеряет права), поэтому проверяем его тестом.
 */
@DisplayName("paper-plugin.yml — манифест и права")
class PluginManifestTest {

    private static final Path MANIFEST = Path.of("src", "main", "resources", "paper-plugin.yml");
    private static final Set<String> VALID_DEFAULTS = Set.of("true", "false", "op", "not op");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> manifest() throws IOException {
        try (InputStream input = Files.newInputStream(MANIFEST)) {
            return new Yaml().load(input);
        }
    }

    @Test
    @DisplayName("обязательные поля заполнены")
    void requiredFieldsArePresent() throws IOException {
        Map<String, Object> manifest = manifest();

        assertAll(
                () -> assertEquals("TerraCraftChat", manifest.get("name")),
                () -> assertEquals("ru.terracraft.chat.TerraCraftChatPlugin", manifest.get("main")),
                () -> assertEquals("26.3", manifest.get("api-version")),
                () -> assertNotNull(manifest.get("version"))
        );
    }

    @Test
    @DisplayName("CommandAPI обязателен, SkinsRestorer/PlaceholderAPI/MineSQL — нет")
    @SuppressWarnings("unchecked")
    void dependencySectionIsCorrect() throws IOException {
        Map<String, Object> manifest = manifest();
        Map<String, Object> server = (Map<String, Object>)
                ((Map<String, Object>) manifest.get("dependencies")).get("server");

        assertAll(
                () -> assertEquals(true, dependency(server, "CommandAPI").get("required")),
                () -> assertEquals(false, dependency(server, "SkinsRestorer").get("required"),
                        "без SkinsRestorer плагин должен грузиться (головы по UUID)"),
                () -> assertEquals(false, dependency(server, "PlaceholderAPI").get("required")),
                () -> assertEquals(false, dependency(server, "MineSQL").get("required"),
                        "без MineSQL чат должен работать, отключая только историю")
        );
    }

    @Test
    @DisplayName("у каждого права есть корректный default")
    @SuppressWarnings("unchecked")
    void permissionsAreWellFormed() throws IOException {
        Map<String, Object> manifest = manifest();
        Map<String, Object> permissions = (Map<String, Object>) manifest.get("permissions");

        assertNotNull(permissions, "секция permissions отсутствует");
        assertTrue(permissions.size() >= 25, "подозрительно мало прав: " + permissions.size());

        List<String> problems = permissions.entrySet().stream()
                .filter(entry -> {
                    if (!(entry.getValue() instanceof Map<?, ?> spec)) return true;
                    Object value = spec.get("default");
                    return value == null || !VALID_DEFAULTS.contains(String.valueOf(value));
                })
                .map(Map.Entry::getKey)
                .toList();

        assertTrue(problems.isEmpty(), () -> "Некорректный default у прав: " + problems);
    }

    @Test
    @DisplayName("все права из кода объявлены в манифесте")
    void allPermissionsUsedInCodeAreDeclared() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> permissions = (Map<String, Object>) manifest().get("permissions");

        List<String> expected = List.of(
                "terracraftchat.color", "terracraftchat.msg", "terracraftchat.reply",
                "terracraftchat.ignore", "terracraftchat.thread", "terracraftchat.nick",
                "terracraftchat.emoji", "terracraftchat.item", "terracraftchat.inv",
                "terracraftchat.inv.admin", "terracraftchat.broadcast", "terracraftchat.admin",
                "terracraftchat.socialspy", "terracraftchat.moderator",
                "terracraftchat.channel.staff", "terracraftchat.stats",
                "terracraftchat.group.create", "terracraftchat.group.use",
                "terracraftchat.group.invite", "terracraftchat.group.leave",
                "terracraftchat.group.delete", "terracraftchat.bypass.ads",
                "terracraftchat.bypass.swear", "terracraftchat.bypass.levenshtein",
                "terracraftchat.bypass.flood", "terracraftchat.bypass.crash",
                "terracraftchat.bypass.cooldown", "terracraftchat.bypass.duplicate"
        );

        List<String> missing = expected.stream()
                .filter(node -> !permissions.containsKey(node))
                .toList();

        assertTrue(missing.isEmpty(), () -> "В paper-plugin.yml нет прав: " + missing);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dependency(Map<String, Object> server, String name) {
        Object dependency = server.get(name);
        assertNotNull(dependency, "нет зависимости " + name);
        return (Map<String, Object>) dependency;
    }
}
