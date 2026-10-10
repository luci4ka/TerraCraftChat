package ru.net.terracraft.chat;

import org.bukkit.entity.Player;
import ru.net.terracraft.chat.config.ChatConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class ChatModerator {

    private final TerraCraftChatPlugin plugin;
    private final ChatConfig config;

    // ===== Кэш по игрокам =====
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final Map<UUID, String> lastMessage = new HashMap<>();

    // ===== Regex =====
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}[.,\\s]?){3}\\d{1,3}\\b"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "\\b[\\w\\-]+\\.(?:com|net|org|ru|gg|xyz|top|io|me|tv|info|biz|site|online|club|shop|fun|space|website|store|tech|app|dev|cloud|host|pro|live|world|life|today|news|media|agency|company|group|team|studio|design|art|blog|page|link|click|download|stream|video|music|game|games)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public ChatModerator(TerraCraftChatPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ===== Результат =====

    public enum Result {
        OK,
        COOLDOWN,
        DUPLICATE,
        SIMILAR,
        FLOOD,
        SWEAR,
        SWEAR_BLOCK,
        ADVERTISEMENT,
        CRASH
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
    }

    // ===== Основная проверка =====

    public CheckResult check(Player sender, String message) {
        ChatConfig.ModerationSection mod = config.moderation();

        // === Bypass-права ===
        boolean bypassAds = sender.hasPermission("terracraftchat.bypass.ads");
        boolean bypassSwear = sender.hasPermission("terracraftchat.bypass.swear");
        boolean bypassLevenshtein = sender.hasPermission("terracraftchat.bypass.levenshtein");
        boolean bypassFlood = sender.hasPermission("terracraftchat.bypass.flood");
        boolean bypassCrash = sender.hasPermission("terracraftchat.bypass.crash");

        // === 1. Anti-crash ===
        if (!bypassCrash && mod.antiCrashEnabled() && containsCrashChars(message)) {
            return new CheckResult(Result.CRASH, message, 0);
        }

        // === 2. Анти-реклама ===
        if (!bypassAds && isAdvertisement(message)) {
            return new CheckResult(Result.ADVERTISEMENT, message, 0);
        }

        // === 3. Cooldown ===
        int cooldown = mod.cooldown();
        if (cooldown > 0 && !sender.hasPermission("terracraftchat.bypass.cooldown")) {
            long now = System.currentTimeMillis();
            long last = lastMessageTime.getOrDefault(sender.getUniqueId(), 0L);
            long elapsed = (now - last) / 1000;

            if (elapsed < cooldown) {
                return new CheckResult(Result.COOLDOWN, message, cooldown - elapsed);
            }
        }

        // === 4. Анти-дубль ===
        String previous = lastMessage.get(sender.getUniqueId());
        if (mod.antiDuplicate()
                && !sender.hasPermission("terracraftchat.bypass.duplicate")
                && previous != null
                && previous.equalsIgnoreCase(message)) {
            return new CheckResult(Result.DUPLICATE, message, 0);
        }

        // === 5. Levenshtein ===
        if (mod.levenshteinEnabled()
                && !bypassLevenshtein
                && previous != null
                && message.length() >= mod.levenshteinMinLength()) {
            double sim = LevenshteinUtil.similarity(previous, message);
            if (sim >= mod.levenshteinThreshold()) {
                return new CheckResult(Result.SIMILAR, message, 0);
            }
        }

        // === 6. Anti-flood ===
        if (mod.maxRepeatingChars() > 0 && !bypassFlood) {
            int maxRepeat = findMaxRepeating(message);
            if (maxRepeat > mod.maxRepeatingChars()) {
                return new CheckResult(Result.FLOOD, message, 0);
            }
        }

        // === 7. Анти-капс ===
        String processed = message;
        if (message.length() >= mod.minCapsLength()) {
            processed = applyAntiCaps(message);
        }

        // === 8. Маты ===
        if (mod.swearFilterEnabled() && !bypassSwear) {
            int swearCount = countSwearWords(processed);

            if (swearCount > mod.maxSwearWords()) {
                return new CheckResult(Result.SWEAR_BLOCK, processed, 0);
            }

            if (swearCount > 0 && mod.swearReplaceInsteadOfBlock()) {
                processed = censorSwear(processed);
                return new CheckResult(Result.SWEAR, processed, 0);
            }
        }

        return new CheckResult(Result.OK, processed, 0);
    }

    // ===== Кэш =====

    public void record(Player sender, String message) {
        lastMessageTime.put(sender.getUniqueId(), System.currentTimeMillis());
        lastMessage.put(sender.getUniqueId(), message);
    }

    public void clear(Player sender) {
        lastMessageTime.remove(sender.getUniqueId());
        lastMessage.remove(sender.getUniqueId());
    }

    // ===== Anti-flood =====

    private int findMaxRepeating(String message) {
        if (message.isEmpty()) return 0;

        int max = 1;
        int current = 1;

        for (int i = 1; i < message.length(); i++) {
            if (message.charAt(i) == message.charAt(i - 1)) {
                current++;
                max = Math.max(max, current);
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
            if (c < 0x20 && c != '\n' && c != '\t' && c != '\r') {
                return true;
            }
            if (c >= 0xE000 && c <= 0xF8FF) {
                return true;
            }
        }
        return false;
    }

    // ===== Анти-капс =====

    private String applyAntiCaps(String message) {
        int letters = 0;
        int uppercase = 0;

        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    uppercase++;
                }
            }
        }

        if (letters == 0) return message;

        double ratio = (double) uppercase / letters;
        if (ratio > config.moderation().maxCapsRatio()) {
            return message.toLowerCase();
        }
        return message;
    }

    // ===== Анти-реклама =====

    private boolean isAdvertisement(String message) {
        if (isWhitelisted(message)) {
            return false;
        }

        ChatConfig.ModerationSection mod = config.moderation();

        if (mod.antiIpEnabled() && IP_PATTERN.matcher(message).find()) {
            return true;
        }
        if (mod.antiUrlEnabled() && URL_PATTERN.matcher(message).find()) {
            return true;
        }
        if (mod.antiDomainEnabled() && DOMAIN_PATTERN.matcher(message).find()) {
            return true;
        }
        return false;
    }

    private boolean isWhitelisted(String message) {
        String lower = message.toLowerCase();
        for (String allowed : config.moderation().allowedDomains()) {
            if (lower.contains(allowed.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // ===== Маты =====

    private int countSwearWords(String message) {
        String lower = message.toLowerCase();
        int count = 0;
        for (String word : config.moderation().swearWords()) {
            String w = word.toLowerCase();
            int index = 0;
            while ((index = lower.indexOf(w, index)) != -1) {
                count++;
                index += w.length();
            }
        }
        return count;
    }

    public String censorSwear(String message) {
        String result = message;
        for (String word : config.moderation().swearWords()) {
            result = result.replaceAll("(?i)" + Pattern.quote(word),
                    config.moderation().swearReplacement());
        }
        return result;
    }
}