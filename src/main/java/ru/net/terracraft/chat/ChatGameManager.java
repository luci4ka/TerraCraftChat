package ru.net.terracraft.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.net.terracraft.chat.config.ChatConfig;
import ru.net.terracraft.chat.game.GameDefinition;
import ru.net.terracraft.chat.game.GameType;
import ru.net.terracraft.chat.game.GamesConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ChatGameManager {

    private final JavaPlugin plugin;
    private final ChatConfig config;
    private final GamesConfig gamesConfig;
    private final Random random = new Random();

    private BukkitTask gameTask;
    private BukkitTask timeoutTask;

    private GameDefinition currentGame;
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

        String type = game.getType().name().toLowerCase();

        // Старт — через i18n
        Bukkit.broadcast(Component.translatable(
                "chatgames." + type + ".start",
                Argument.string("word", currentPrompt)
        ));

        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!gameActive) return;
            gameActive = false;

            Bukkit.broadcast(Component.translatable(
                    "chatgames." + type + ".timeout",
                    Argument.string("answer", currentAnswer)
            ));
            timeoutTask = null;
        }, config.chatGames().duration() * 20L);
    }

    /**
     * Готовит задание и устанавливает currentAnswer + currentPrompt.
     * Возвращает false, если игру нельзя запустить.
     */
    private boolean preparePrompt(GameDefinition game) {
        switch (game.getType()) {
            case UNSCRAMBLE -> {
                if (game.getWords().isEmpty()) return false;
                String word = game.getWords().get(random.nextInt(game.getWords().size()));
                currentAnswer = word.toLowerCase();
                currentPrompt = scramble(word);
                return true;
            }
            case QUIZ -> {
                if (game.getQuestions().isEmpty()) return false;
                GameDefinition.Question q = game.getQuestions()
                        .get(random.nextInt(game.getQuestions().size()));
                currentAnswer = q.getAnswers().get(0).toLowerCase();
                currentPrompt = q.getQuestion();
                return true;
            }
            case MATH -> {
                List<String> ops = game.getOperations().isEmpty()
                        ? List.of("add")
                        : game.getOperations();
                String op = ops.get(random.nextInt(ops.size()));
                int a = random.nextInt(game.getMaxNumber() - game.getMinNumber() + 1) + game.getMinNumber();
                int b = random.nextInt(game.getMaxNumber() - game.getMinNumber() + 1) + game.getMinNumber();

                int answer;
                String symbol;
                switch (op) {
                    case "sub" -> { answer = a - b; symbol = "-"; }
                    case "mul" -> { answer = a * b; symbol = "*"; }
                    case "div" -> {
                        b = Math.max(1, b);
                        a = a * b;
                        answer = a / b;
                        symbol = "/";
                    }
                    default -> { answer = a + b; symbol = "+"; }
                }
                currentAnswer = String.valueOf(answer);
                currentPrompt = a + " " + symbol + " " + b;
                return true;
            }
        }
        return false;
    }

    public boolean checkAnswer(Player player, String message) {
        if (!gameActive) return false;
        if (!message.trim().equalsIgnoreCase(currentAnswer)) return false;

        gameActive = false;

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        long time = (System.currentTimeMillis() - gameStartTime) / 1000;
        String type = currentGame.getType().name().toLowerCase();

        Bukkit.broadcast(Component.translatable(
                "chatgames." + type + ".win",
                Argument.component("player", player.displayName()),
                Argument.string("answer", currentAnswer),
                Argument.string("time", String.valueOf(time))
        ));

        // Награды
        for (String reward : currentGame.getRewards()) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    reward.replace("{player}", player.getName())
            );
        }

        return true;
    }

    // ===== Выбор игры по весам =====

    private GameDefinition pickWeighted(List<GameDefinition> games) {
        int total = games.stream().mapToInt(GameDefinition::getWeight).sum();
        if (total <= 0) return games.get(random.nextInt(games.size()));

        int roll = random.nextInt(total);
        int acc = 0;
        for (GameDefinition game : games) {
            acc += game.getWeight();
            if (roll < acc) return game;
        }
        return games.get(games.size() - 1);
    }

    // ===== Утилиты =====

    private String scramble(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        Collections.shuffle(chars);

        StringBuilder sb = new StringBuilder();
        for (char c : chars) sb.append(c);
        return sb.toString();
    }
}