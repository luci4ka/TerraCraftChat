package ru.terracraft.chat.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Аудит локализации: ни один ключ, использованный в коде, не должен
 * отсутствовать в файлах {@code lang/messages_*.yml}, а наборы ключей
 * во всех языках должны совпадать.
 *
 * <p>Такой тест ловит самую частую регрессию локализации — «добавил
 * {@code MessageUtil.send(sender, "chat.new-thing")}, а ключ забыл».
 */
@DisplayName("Локализация — ключи в коде и в lang-файлах")
class LocaleKeysTest {

    private static final Path LANG_DIR = Path.of("src", "main", "resources", "lang");
    private static final Path SOURCE_DIR = Path.of("src", "main", "java");

    private static final Pattern KEY_IN_CODE =
            Pattern.compile("\"(chat|chatgames|autobroadcast)\\.[A-Za-z0-9_.\\-]+\"");

    private static final Pattern YAML_ENTRY =
            Pattern.compile("^(\\s*)([A-Za-z0-9_.\\-]+):(.*)$");

    private record Frame(int indent, String key) {}

    /** Плоский список ключей YAML: {@code chat: -> prefix:} становится {@code chat.prefix}. */
    private static Map<String, String> flattenYaml(Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        Deque<Frame> stack = new ArrayDeque<>();

        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;

            Matcher matcher = YAML_ENTRY.matcher(raw);
            if (!matcher.matches()) continue;

            int indent = matcher.group(1).length();
            String key = matcher.group(2);
            String value = matcher.group(3).trim();

            while (!stack.isEmpty() && stack.peekLast().indent() >= indent) {
                stack.pollLast();
            }
            stack.addLast(new Frame(indent, key));

            if (!value.isEmpty()) {
                result.put(stack.stream().map(Frame::key).collect(Collectors.joining(".")), value);
            }
        }
        return result;
    }

    private static Map<String, Set<String>> loadLocales() throws IOException {
        Map<String, Set<String>> locales = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(LANG_DIR)) {
            List<Path> yamlFiles = files
                    .filter(path -> path.getFileName().toString().startsWith("messages_"))
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();

            for (Path file : yamlFiles) {
                locales.put(file.getFileName().toString(), flattenYaml(file).keySet());
            }
        }
        return locales;
    }

    private static Set<String> keysUsedInCode() throws IOException {
        Set<String> used = new TreeSet<>();

        try (Stream<Path> files = Files.walk(SOURCE_DIR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = KEY_IN_CODE.matcher(source);
                while (matcher.find()) {
                    used.add(matcher.group().replace("\"", ""));
                }
            }
        }
        return used;
    }

    @Test
    @DisplayName("все ключи из кода есть во всех языках")
    void everyKeyUsedInCodeExistsInEveryLocale() throws IOException {
        Map<String, Set<String>> locales = loadLocales();
        Set<String> used = keysUsedInCode();

        assertFalse(locales.isEmpty(), "не найдено ни одного файла lang/messages_*.yml");
        assertTrue(used.size() > 30, "подозрительно мало ключей найдено в коде: " + used.size());

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> locale : locales.entrySet()) {
            Set<String> missing = new LinkedHashSet<>(used);
            missing.removeAll(locale.getValue());
            for (String key : missing) {
                problems.add(locale.getKey() + ": нет ключа " + key);
            }
        }

        assertTrue(problems.isEmpty(), () -> "Отсутствующие ключи локализации:\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("наборы ключей во всех языках совпадают")
    void localeKeySetsAreIdentical() throws IOException {
        Map<String, Set<String>> locales = loadLocales();
        Set<String> reference = locales.values().iterator().next();

        List<String> problems = new ArrayList<>();
        locales.forEach((name, keys) -> {
            Set<String> missing = new LinkedHashSet<>(reference);
            missing.removeAll(keys);
            Set<String> extra = new LinkedHashSet<>(keys);
            extra.removeAll(reference);

            if (!missing.isEmpty()) problems.add(name + " не хватает: " + missing);
            if (!extra.isEmpty()) problems.add(name + " лишние: " + extra);
        });

        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
    }

    @Test
    @DisplayName("ключ chat.prefix объявлен и не пустой во всех языках")
    void prefixKeyIsPresent() throws IOException {
        Map<String, Set<String>> locales = loadLocales();

        assertAll(locales.keySet().stream()
                .map(name -> (org.junit.jupiter.api.function.Executable) () ->
                        assertTrue(locales.get(name).contains("chat.prefix"),
                                name + ": нет chat.prefix")))
        ;
    }
}
