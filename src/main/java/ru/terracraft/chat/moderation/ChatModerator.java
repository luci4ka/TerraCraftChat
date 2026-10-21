package ru.terracraft.chat.moderation;

import ru.terracraft.chat.util.LevenshteinUtil;
import ru.terracraft.chat.config.ChatConfig;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatModerator {

    private volatile ChatConfig config;
    private volatile String swearReplacement;

    // Потокобезопасные кэши
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();

    // volatile: reload подменяет список целиком, check() видит либо старый, либо новый
    private volatile List<Pattern> swearPatterns = List.of();

    // ===== Regex =====
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "\\b[\\w\\-]{3,}\\.(?:com|net|org|ru|gg|xyz|top|io|me|tv|info|biz|site|online|club|shop|fun|space|website|store|tech|app|dev|cloud|host|pro|live|world|life|today|news|media|agency|company|group|team|studio|design|art|blog|page|link|click|download|stream|video|music|game|games)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public ChatModerator(ChatConfig config) {
        this.config = config;
        this.swearReplacement = config.moderation().swearReplacement();
        compileSwearPatterns();
    }

    /**
     * Перекомпилирует паттерны при reload.
     * Потокобезопасно: check() в async, reload() в main.
     */
    public void reload(ChatConfig newConfig) {
        this.config = newConfig;
        this.swearReplacement = newConfig.moderation().swearReplacement();
        compileSwearPatterns();
        // lastMessageTime/lastMessage НЕ трогаем — защита от обхода cooldown
    }

    private void compileSwearPatterns() {
        List<Pattern> list = new ArrayList<>();
        for (String word : config.moderation().swearWords()) {
            if (word == null) continue;
            String trimmed = word.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) continue;

            try {
                list.add(Pattern.compile(
                        "(?<![\\p{L}])" + Pattern.quote(trimmed) + "(?![\\p{L}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ));
            } catch (Exception ex) {
                // пропускаем битые паттерны
            }
        }
        // Атомарная подмена — читатели видят либо старый, либо новый список
        this.swearPatterns = List.copyOf(list);
    }

    // ===== Результат =====

    public enum Result {
        OK, COOLDOWN, DUPLICATE, SIMILAR, FLOOD,
        SWEAR, SWEAR_BLOCK, ADVERTISEMENT, CRASH
    }

    public static class CheckResult {
        private final Result result;
        private final String message;
        private final long cooldownRemaining;

        public CheckResult(Result result, String message, long cooldownRemaining) {
            this.result = result;
            this.message = message;
            this.cooldownRemaining = cooldownRemaining;
        }

        public Result result() { return result; }
        public String message() { return message; }
        public long cooldownRemaining() { return cooldownRemaining; }

        public boolean isBlocked() {
            return result != Result.OK && result != Result.SWEAR;
        }
    }

    public static final class Bypass {
        public final boolean ads, swear, levenshtein, flood, crash, cooldown, duplicate;

        public Bypass(boolean ads, boolean swear, boolean levenshtein,
                      boolean flood, boolean crash, boolean cooldown, boolean duplicate) {
            this.ads = ads;
            this.swear = swear;
            this.levenshtein = levenshtein;
            this.flood = flood;
            this.crash = crash;
            this.cooldown = cooldown;
            this.duplicate = duplicate;
        }
    }

    // ===== Основная проверка =====

    public CheckResult check(UUID senderId, String message, Bypass bypass) {
        ChatConfig.ModerationSection mod = config.moderation();

        if (!bypass.crash && mod.antiCrashEnabled() && containsCrashChars(message)) {
            return new CheckResult(Result.CRASH, message, 0);
        }

        if (!bypass.ads && isAdvertisement(message)) {
            return new CheckResult(Result.ADVERTISEMENT, message, 0);
        }

        int cooldown = mod.cooldown();
        if (cooldown > 0 && !bypass.cooldown) {
            long now = System.currentTimeMillis();
            long last = lastMessageTime.getOrDefault(senderId, 0L);
            long elapsed = (now - last) / 1000;
            if (elapsed < cooldown) {
                return new CheckResult(Result.COOLDOWN, message, cooldown - elapsed);
            }
        }

        String previous = lastMessage.get(senderId);
        if (mod.antiDuplicate() && !bypass.duplicate
                && previous != null && previous.equalsIgnoreCase(message)) {
            return new CheckResult(Result.DUPLICATE, message, 0);
        }

        if (mod.levenshteinEnabled() && !bypass.levenshtein
                && previous != null && message.length() >= mod.levenshteinMinLength()) {
            double sim = LevenshteinUtil.similarity(previous, message);
            if (sim >= mod.levenshteinThreshold()) {
                return new CheckResult(Result.SIMILAR, message, 0);
            }
        }

        if (mod.maxRepeatingChars() > 0 && !bypass.flood) {
            if (findMaxRepeating(message) > mod.maxRepeatingChars()) {
                return new CheckResult(Result.FLOOD, message, 0);
            }
        }

        String processed = message;
        if (message.length() >= mod.minCapsLength()) {
            processed = applyAntiCaps(message);
        }

        // ===== Маты =====
        if (mod.swearFilterEnabled() && !bypass.swear) {
            String normalized = normalizeForSwear(processed);
            int swearCount = countSwear(normalized);

            if (swearCount == 0) {
                return new CheckResult(Result.OK, processed, 0);
            }

            if (swearCount > mod.maxSwearWords()) {
                return new CheckResult(Result.SWEAR_BLOCK, processed, 0);
            }

            if (!mod.swearReplaceInsteadOfBlock()) {
                return new CheckResult(Result.SWEAR_BLOCK, processed, 0);
            }

            String censored = censorSwear(processed);
            // Гомоглиф-мат не нашёлся в оригинале — цензурируем нормализованную строку
            if (censored.equals(processed)) {
                censored = censorSwear(normalized);
            }
            return new CheckResult(Result.SWEAR, censored, 0);
        }

        return new CheckResult(Result.OK, processed, 0);
    }

    // ===== Запись состояния =====

    public void record(UUID senderId, String message) {
        lastMessageTime.put(senderId, System.currentTimeMillis());
        lastMessage.put(senderId, message);
    }

    public void clear(UUID senderId) {
        lastMessageTime.remove(senderId);
        lastMessage.remove(senderId);
    }

    // ===== Нормализация =====

    private String normalizeForSwear(String message) {
        String s = Normalizer.normalize(message, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);

        // Убираем пробелы и пунктуацию между буквами: "х у й" → "хуй"
        s = s.replaceAll("(?<=\\p{L})[\\s._*\\-]+(?=\\p{L})", "");

        // Замена латинских омоглифов на кириллицу
        s = s.replace('a', 'а').replace('e', 'е').replace('o', 'о')
                .replace('p', 'р').replace('c', 'с').replace('x', 'х')
                .replace('y', 'у').replace('k', 'к').replace('m', 'м')
                .replace('t', 'т').replace('h', 'н').replace('b', 'в');

        return s;
    }

    // ===== Маты =====

    private int countSwear(String normalized) {
        int count = 0;
        for (Pattern p : swearPatterns) {
            Matcher m = p.matcher(normalized);
            while (m.find()) {
                count++;
                if (count > config.moderation().maxSwearWords()) {
                    return count;
                }
            }
        }
        return count;
    }

    public String censorSwear(String message) {
        String result = message;
        for (Pattern p : swearPatterns) {
            result = p.matcher(result).replaceAll(
                    Matcher.quoteReplacement(swearReplacement)
            );
        }
        return result;
    }

    // ===== Anti-flood =====

    private int findMaxRepeating(String message) {
        if (message.isEmpty()) return 0;
        int max = 1, current = 1;
        for (int i = 1; i < message.length(); i++) {
            if (message.charAt(i) == message.charAt(i - 1)) {
                max = Math.max(max, ++current);
            } else {
                current = 1;
            }
        }
        return max;
    }

    // ===== Anti-crash =====

    private boolean containsCrashChars(String message) {
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t' && c != '\r') return true;
            if (c >= 0xE000 && c <= 0xF8FF) return true;
        }
        return false;
    }

    // ===== Анти-капс =====

    private String applyAntiCaps(String message) {
        int letters = 0, uppercase = 0;
        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) uppercase++;
            }
        }
        if (letters == 0) return message;
        if ((double) uppercase / letters > config.moderation().maxCapsRatio()) {
            return message.toLowerCase(Locale.ROOT);
        }
        return message;
    }

    // ===== Анти-реклама =====

    private boolean isAdvertisement(String message) {
        if (isWhitelisted(message)) return false;

        String normalized = message.replaceAll("\\s*\\.\\s*", ".");

        ChatConfig.ModerationSection mod = config.moderation();
        if (mod.antiIpEnabled() && IP_PATTERN.matcher(normalized).find()) return true;
        if (mod.antiUrlEnabled() && URL_PATTERN.matcher(normalized).find()) return true;
        if (mod.antiDomainEnabled() && DOMAIN_PATTERN.matcher(normalized).find()) return true;
        return false;
    }

    private boolean isWhitelisted(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (String allowed : config.moderation().allowedDomains()) {
            String a = allowed.toLowerCase(Locale.ROOT);
            Pattern p = Pattern.compile(
                    "(?<![\\w.\\-])" + Pattern.quote(a) + "(?![\\w.\\-])"
            );
            Matcher m = p.matcher(lower);
            if (!m.find()) continue;

            String remainder = m.replaceAll(" ");

            if (!DOMAIN_PATTERN.matcher(remainder).find()
                    && !IP_PATTERN.matcher(remainder).find()
                    && !URL_PATTERN.matcher(remainder).find()) {
                return true;
            }
        }
        return false;
    }
}