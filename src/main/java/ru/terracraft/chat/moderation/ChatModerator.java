package ru.terracraft.chat.moderation;

import ru.terracraft.chat.config.ChatConfig;
import ru.terracraft.chat.util.LevenshteinUtil;

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

    /** Максимальная длина строки, для которой считаем схожесть (защита от O(n²) на «простынях»). */
    private static final int SIMILARITY_MAX_LENGTH = 256;

    private volatile ChatConfig config;
    private volatile String swearReplacement;

    // Потокобезопасные кэши
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();

    // volatile: reload подменяет списки целиком, check() видит либо старые, либо новые
    private volatile List<Pattern> swearPatterns = List.of();
    private volatile List<Pattern> allowedDomainPatterns = List.of();

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

    /** Разделители внутри слова, которые допускаются в матерных паттернах. */
    private static final String GAP = "[\\s._*\\-]{0,3}";

    public ChatModerator(ChatConfig config) {
        this.config = config;
        compilePatterns(config);
    }

    /**
     * Перекомпилирует паттерны при reload.
     * Потокобезопасно: check() в async, reload() в main.
     */
    public void reload(ChatConfig newConfig) {
        this.config = newConfig;
        compilePatterns(newConfig);
        // lastMessageTime/lastMessage НЕ трогаем — защита от обхода cooldown
    }

    private void compilePatterns(ChatConfig source) {
        this.swearReplacement = source.moderation().swearReplacement();

        List<Pattern> swear = new ArrayList<>();
        for (String word : source.moderation().swearWords()) {
            if (word == null) continue;
            String trimmed = word.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) continue;

            try {
                swear.add(Pattern.compile(
                        "(?<![\\p{L}])" + gapTolerant(trimmed) + "(?![\\p{L}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ));
            } catch (Exception ex) {
                // пропускаем битые паттерны
            }
        }
        this.swearPatterns = List.copyOf(swear);

        // Белый список доменов компилируем один раз, а не на каждое сообщение
        List<Pattern> allowed = new ArrayList<>();
        for (String domain : source.moderation().allowedDomains()) {
            if (domain == null || domain.isBlank()) continue;
            try {
                allowed.add(Pattern.compile(
                        "(?<![\\w.\\-])" + Pattern.quote(domain.trim().toLowerCase(Locale.ROOT))
                                + "(?![\\w.\\-])"
                ));
            } catch (Exception ex) {
                // пропускаем битые паттерны
            }
        }
        this.allowedDomainPatterns = List.copyOf(allowed);
    }

    /**
     * Собирает regex, который находит слово даже с разделителями между буквами:
     * «сука», «с у к а», «с.у.к.а» — всё считается матом.
     * Раньше для этого из текста удалялись все пробелы между буквами, из-за чего
     * «ты сука» превращалось в «тысука» и фильтр перестарался — слово с нормальным
     * пробелом перед ним больше не находилось.
     */
    private static String gapTolerant(String word) {
        StringBuilder sb = new StringBuilder(word.length() * 8);
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) sb.append(GAP);
            sb.append(Pattern.quote(String.valueOf(word.charAt(i))));
        }
        return sb.toString();
    }

    // ============================================================
    //  РЕЗУЛЬТАТ ПРОВЕРКИ
    // ============================================================

    public enum Result {
        OK, COOLDOWN, DUPLICATE, SIMILAR, FLOOD,
        SWEAR, SWEAR_BLOCK, ADVERTISEMENT, CRASH
    }

    /**
     * Итог проверки сообщения — sealed-иерархия вместо «enum + флаги»:
     * компилятор требует обработать все ветки (см. ChatListener).
     */
    public sealed interface Outcome {

        /** Сообщение можно рассылать как есть. */
        record Allowed(String message) implements Outcome {}

        /** Сообщение рассылается, но с заменённым матом. */
        record Censored(String message) implements Outcome {}

        /** Сообщение блокируется. */
        record Blocked(Result reason, long cooldownRemaining) implements Outcome {}
    }

    public record Bypass(boolean ads, boolean swear, boolean levenshtein,
                         boolean flood, boolean crash, boolean cooldown,
                         boolean duplicate) {}

    // ============================================================
    //  ОСНОВНАЯ ПРОВЕРКА
    // ============================================================

    public Outcome check(UUID senderId, String message, Bypass bypass) {
        ChatConfig.ModerationSection mod = config.moderation();

        if (!bypass.crash() && mod.antiCrashEnabled() && containsCrashChars(message)) {
            return new Outcome.Blocked(Result.CRASH, 0);
        }

        if (!bypass.ads() && isAdvertisement(message)) {
            return new Outcome.Blocked(Result.ADVERTISEMENT, 0);
        }

        int cooldown = mod.cooldown();
        if (cooldown > 0 && !bypass.cooldown()) {
            long now = System.currentTimeMillis();
            long last = lastMessageTime.getOrDefault(senderId, 0L);
            long elapsed = (now - last) / 1000;
            if (elapsed < cooldown) {
                return new Outcome.Blocked(Result.COOLDOWN, cooldown - elapsed);
            }
        }

        String previous = lastMessage.get(senderId);
        if (mod.antiDuplicate() && !bypass.duplicate()
                && previous != null && previous.equalsIgnoreCase(message)) {
            return new Outcome.Blocked(Result.DUPLICATE, 0);
        }

        if (mod.levenshteinEnabled() && !bypass.levenshtein()
                && previous != null
                && message.length() >= mod.levenshteinMinLength()
                && message.length() <= SIMILARITY_MAX_LENGTH
                && previous.length() <= SIMILARITY_MAX_LENGTH) {
            double similarity = LevenshteinUtil.similarity(previous, message);
            if (similarity >= mod.levenshteinThreshold()) {
                return new Outcome.Blocked(Result.SIMILAR, 0);
            }
        }

        if (mod.maxRepeatingChars() > 0 && !bypass.flood()) {
            if (findMaxRepeating(message) > mod.maxRepeatingChars()) {
                return new Outcome.Blocked(Result.FLOOD, 0);
            }
        }

        String processed = message;
        if (message.length() >= mod.minCapsLength()) {
            processed = applyAntiCaps(message);
        }

        // ===== Маты =====
        if (mod.swearFilterEnabled() && !bypass.swear()) {
            // 1) прямое совпадение (в т.ч. с разделителями между буквами)
            int swearCount = countSwear(processed);

            // 2) с заменой гомоглифов (лат. c → рус. с) и NFKC-нормализацией
            String normalized = normalizeForSwear(processed);
            if (!normalized.equals(processed.toLowerCase(Locale.ROOT))) {
                swearCount = Math.max(swearCount, countSwear(normalized));
            }

            if (swearCount == 0) {
                return new Outcome.Allowed(processed);
            }

            if (swearCount > mod.maxSwearWords()) {
                return new Outcome.Blocked(Result.SWEAR_BLOCK, 0);
            }

            if (!mod.swearReplaceInsteadOfBlock()) {
                return new Outcome.Blocked(Result.SWEAR_BLOCK, 0);
            }

            String censored = censorSwear(processed);
            // Гомоглиф-мат не нашёлся в оригинале — цензурируем нормализованную строку
            if (censored.equals(processed)) {
                censored = censorSwear(normalized);
            }
            return new Outcome.Censored(censored);
        }

        return new Outcome.Allowed(processed);
    }

    // ============================================================
    //  ЗАПИСЬ СОСТОЯНИЯ
    // ============================================================

    public void record(UUID senderId, String message) {
        lastMessageTime.put(senderId, System.currentTimeMillis());
        lastMessage.put(senderId, message);
    }

    public void clear(UUID senderId) {
        lastMessageTime.remove(senderId);
        lastMessage.remove(senderId);
    }

    // ============================================================
    //  НОРМАЛИЗАЦИЯ
    // ============================================================

    /** «х у й» / «х.у.й» ловятся самими паттернами — здесь только NFKC + гомоглифы. */
    private String normalizeForSwear(String message) {
        String s = Normalizer.normalize(message, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);

        // Замена латинских омоглифов на кириллицу (длина строки не меняется)
        s = s.replace('a', 'а').replace('e', 'е').replace('o', 'о')
                .replace('p', 'р').replace('c', 'с').replace('x', 'х')
                .replace('y', 'у').replace('k', 'к').replace('m', 'м')
                .replace('t', 'т').replace('h', 'н').replace('b', 'в');

        return s;
    }

    // ============================================================
    //  МАТЫ
    // ============================================================

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

    // ============================================================
    //  ANTI-FLOOD
    // ============================================================

    private int findMaxRepeating(String message) {
        if (message.isEmpty()) return 0;
        int max = 1;
        int current = 1;
        for (int i = 1; i < message.length(); i++) {
            if (message.charAt(i) == message.charAt(i - 1)) {
                max = Math.max(max, ++current);
            } else {
                current = 1;
            }
        }
        return max;
    }

    // ============================================================
    //  ANTI-CRASH
    // ============================================================

    private boolean containsCrashChars(String message) {
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t' && c != '\r') return true;
            if (c >= 0xE000 && c <= 0xF8FF) return true;
        }
        return false;
    }

    // ============================================================
    //  АНТИ-КАПС
    // ============================================================

    private String applyAntiCaps(String message) {
        int letters = 0;
        int uppercase = 0;
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

    // ============================================================
    //  АНТИ-РЕКЛАМА
    // ============================================================

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

        for (Pattern pattern : allowedDomainPatterns) {
            Matcher m = pattern.matcher(lower);
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
