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

    private static final long WRONG_ANSWER_COOLDOWN_MS = 3000L;

    private final JavaPlugin plugin;
    private ChatConfig config;          // ← не final
    private final GamesConfig gamesConfig;
    private final Random random = new Random();

    private final Map<String, ShuffleBag<String>> wordBags = new ConcurrentHashMap<>();
    private final Map<String, ShuffleBag<GameDefinition.Question>> questionBags = new ConcurrentHashMap<>();
    private final Map<UUID, Long> wrongAnswerCooldowns = new ConcurrentHashMap<>();

    private BukkitTask gameTask;
    private BukkitTask timeoutTask;

    private GameDefinition currentGame;
    private GameDefinition.Question currentQuestion;
    private String currentAnswer;
    private String currentPrompt;
    private long gameStartTime;
    private volatile boolean gameActive = false;
    private volatile boolean manuallyDisabled = false;   // ← toggle

    public ChatGameManager(JavaPlugin plugin, ChatConfig config, GamesConfig gamesConfig) {
        this.plugin = plugin;
        this.config = config;
        this.gamesConfig = gamesConfig;
    }

    // ===== Toggle =====

    /** Возвращает true, если игры теперь включены. */
    public boolean toggle() {
        manuallyDisabled = !manuallyDisabled;
        if (manuallyDisabled) {
            stop();
        } else {
            start();
        }
        return !manuallyDisabled;
    }

    public boolean isManuallyDisabled() {
        return manuallyDisabled;
    }

    /** Reload конфига без потери manuallyDisabled. */
    public void reload(ChatConfig newConfig) {
        this.config = newConfig;
        stop();
        if (!manuallyDisabled) start();
    }

    // ===== Управление =====

    public void start() {
        if (!config.chatGames().enabled()) return;
        if (manuallyDisabled) return;
        if (gameTask != null && !gameTask.isCancelled()) return;

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
        if (manuallyDisabled) return;
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
        if (!gameActive || manuallyDisabled) return false;

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

    private String scramble(String word) {
        if (word == null || word.length() < 2) return word;
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        String original = word.toLowerCase(Locale.ROOT);
        for (int attempt = 0; attempt < 5; attempt++) {
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            if (!sb.toString().equalsIgnoreCase(original)) return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    public boolean hasActiveGame() {
        return gameActive;
    }
}