package ru.net.terracraft.chat.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.net.terracraft.chat.config.ChatConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatGameManager {

    private static final long WRONG_ANSWER_COOLDOWN_MS = 3000L; // 3 сек

    private final JavaPlugin plugin;
    private final ChatConfig config;
    private final GamesConfig gamesConfig;
    private final Random random = new Random();

    // ShuffleBag для слов и вопросов — чтобы не повторялись
    private final Map<String, ShuffleBag<String>> wordBags = new ConcurrentHashMap<>();
    private final Map<String, ShuffleBag<GameDefinition.Question>> questionBags = new ConcurrentHashMap<>();

    // Cooldown на неправильные ответы
    private final Map<UUID, Long> wrongAnswerCooldowns = new ConcurrentHashMap<>();

    private BukkitTask gameTask;
    private BukkitTask timeoutTask;

    private GameDefinition currentGame;
    private GameDefinition.Question currentQuestion; // для QUIZ
    private String currentAnswer;
    private String currentPrompt;
    private long gameStartTime;
    private volatile boolean gameActive = false;

    public ChatGameManager(JavaPlugin plugin, ChatConfig config, GamesConfig gamesConfig) {
        this.plugin = plugin;
        this.config = config;
        this.gamesConfig = gamesConfig;
    }

    // ===== Управление =====

    public void start() {
        if (!config.chatGames().enabled()) return;

        int interval = Math.max(30, config.chatGames().interval()) * 20;
        gameTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::startGame, interval, interval
        );
    }

    public void stop() {
        if (gameTask != null) gameTask.cancel();
        if (timeoutTask != null) timeoutTask.cancel();
        gameTask = null;
        timeoutTask = null;
        gameActive = false;
        wrongAnswerCooldowns.clear();
    }

    // ===== Игра =====

    private void startGame() {
        if (gameActive) return;
        if (Bukkit.getOnlinePlayers().size() < config.chatGames().minPlayers()) return;

        List<GameDefinition> games = gamesConfig.getGames();
        if (games.isEmpty()) return;

        GameDefinition game = pickWeighted(games);
        if (game == null) return;

        if (!preparePrompt(game)) return;

        currentGame = game;
        gameStartTime = System.currentTimeMillis();
        gameActive = true;
        wrongAnswerCooldowns.clear();

        String type = game.type().name().toLowerCase(Locale.ROOT);

        Bukkit.broadcast(Component.translatable(
                "chatgames." + type + ".start",
                Argument.string("prompt", currentPrompt)
        ));

        // timeout per-game: если в конфиге 0 — глобальный
        int durationSec = game.timeoutSeconds() > 0
                ? game.timeoutSeconds()
                : config.chatGames().duration();

        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!gameActive) return;
            gameActive = false;

            Bukkit.broadcast(Component.translatable(
                    "chatgames." + type + ".timeout",
                    Argument.string("answer", currentAnswer)
            ));
            timeoutTask = null;
        }, durationSec * 20L);
    }

    /**
     * Готовит задание и устанавливает currentAnswer + currentPrompt.
     * Возвращает false, если игру нельзя запустить.
     */
    private boolean preparePrompt(GameDefinition game) {
        currentQuestion = null;

        return switch (game.type()) {
            case UNSCRAMBLE -> prepareUnscramble(game);
            case QUIZ       -> prepareQuiz(game);
            case MATH       -> prepareMath(game);
        };
    }

    private boolean prepareUnscramble(GameDefinition game) {
        if (game.words().isEmpty()) return false;

        String word = wordBags
                .computeIfAbsent(game.id(), k -> new ShuffleBag<>(game.words()))
                .next();

        currentAnswer = word.toLowerCase(Locale.ROOT);
        currentPrompt = scramble(word);
        return true;
    }

    private boolean prepareQuiz(GameDefinition game) {
        if (game.questions().isEmpty()) return false;

        GameDefinition.Question q = questionBags
                .computeIfAbsent(game.id(), k -> new ShuffleBag<>(game.questions()))
                .next();

        if (q.answers().isEmpty()) return false;

        currentQuestion = q;
        currentAnswer = q.answers().get(0);
        currentPrompt = q.question();
        return true;
    }

    private boolean prepareMath(GameDefinition game) {
        List<String> ops = game.operations().isEmpty()
                ? List.of("add")
                : game.operations();
        String op = ops.get(random.nextInt(ops.size()));

        int min = Math.min(game.minNumber(), game.maxNumber());
        int max = Math.max(game.minNumber(), game.maxNumber());

        int a = random.nextInt(max - min + 1) + min;
        int b = random.nextInt(max - min + 1) + min;

        int answer;
        String symbol;

        switch (op) {
            case "sub" -> { answer = a - b; symbol = "-"; }
            case "mul" -> { answer = a * b; symbol = "*"; }
            case "div" -> {
                // Честное деление: b > 0, a кратно b.
                b = Math.max(1, b);
                answer = a / b;
                a = answer * b;
                symbol = "/";
            }
            default -> { answer = a + b; symbol = "+"; }
        }

        currentAnswer = String.valueOf(answer);
        currentPrompt = a + " " + symbol + " " + b;
        return true;
    }

    public boolean checkAnswer(Player player, String message) {
        if (!gameActive) return false;

        // Cooldown на неправильные ответы — чтобы не спамили
        UUID uuid = player.getUniqueId();
        Long lastWrong = wrongAnswerCooldowns.get(uuid);
        if (lastWrong != null
                && System.currentTimeMillis() - lastWrong < WRONG_ANSWER_COOLDOWN_MS) {
            return false;
        }

        if (!matches(message)) {
            wrongAnswerCooldowns.put(uuid, System.currentTimeMillis());
            return false;
        }

        gameActive = false;

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        long time = (System.currentTimeMillis() - gameStartTime) / 1000;
        String type = currentGame.type().name().toLowerCase(Locale.ROOT);

        Bukkit.broadcast(Component.translatable(
                "chatgames." + type + ".win",
                Argument.component("player", player.displayName()),
                Argument.string("answer", currentAnswer),
                Argument.string("time", String.valueOf(time))
        ));

        // Награды
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (String reward : currentGame.rewards()) {
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        reward.replace("{player}", player.getName())
                );
            }
        });

        wrongAnswerCooldowns.clear();
        return true;
    }

    private boolean matches(String input) {
        if (input == null) return false;
        String trimmed = input.trim();

        if (currentGame != null
                && currentGame.type() == GameType.QUIZ
                && currentQuestion != null) {
            return currentQuestion.matches(trimmed);
        }

        return currentAnswer != null && trimmed.equalsIgnoreCase(currentAnswer);
    }

    // ===== Выбор игры по весам =====

    private GameDefinition pickWeighted(List<GameDefinition> games) {
        int total = games.stream().mapToInt(GameDefinition::weight).sum();
        if (total <= 0) return games.get(random.nextInt(games.size()));

        int roll = random.nextInt(total);
        int acc = 0;
        for (GameDefinition game : games) {
            acc += game.weight();
            if (roll < acc) return game;
        }
        return games.get(games.size() - 1);
    }

    // ===== Утилиты =====

    private String scramble(String word) {
        if (word == null || word.length() < 2) return word;

        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);

        String original = word.toLowerCase(Locale.ROOT);
        for (int attempt = 0; attempt < 5; attempt++) {
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            if (!sb.toString().equalsIgnoreCase(original)) {
                return sb.toString();
            }
        }

        StringBuilder sb = new StringBuilder();
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    public boolean hasActiveGame() {
        return gameActive;
    }
}