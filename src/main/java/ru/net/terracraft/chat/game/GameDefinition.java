package ru.net.terracraft.chat.game;

import java.util.List;
import java.util.Map;

public class GameDefinition {

    private final String id;
    private final GameType type;
    private final int weight;
    private final List<String> rewards;

    // Для unscramble
    private final List<String> words;

    // Для quiz
    private final List<Question> questions;

    // Для math
    private final List<String> operations;
    private final int minNumber;
    private final int maxNumber;

    public GameDefinition(String id, GameType type, int weight, List<String> rewards,
                          List<String> words, List<Question> questions,
                          List<String> operations, int minNumber, int maxNumber) {
        this.id = id;
        this.type = type;
        this.weight = weight;
        this.rewards = rewards;
        this.words = words;
        this.questions = questions;
        this.operations = operations;
        this.minNumber = minNumber;
        this.maxNumber = maxNumber;
    }

    public String getId() { return id; }
    public GameType getType() { return type; }
    public int getWeight() { return weight; }
    public List<String> getRewards() { return rewards; }
    public List<String> getWords() { return words; }
    public List<Question> getQuestions() { return questions; }
    public List<String> getOperations() { return operations; }
    public int getMinNumber() { return minNumber; }
    public int getMaxNumber() { return maxNumber; }

    public static class Question {
        private final String question;
        private final List<String> answers;
        private final boolean caseSensitive;

        public Question(String question, List<String> answers, boolean caseSensitive) {
            this.question = question;
            this.answers = answers;
            this.caseSensitive = caseSensitive;
        }

        public String getQuestion() { return question; }
        public List<String> getAnswers() { return answers; }
        public boolean isCaseSensitive() { return caseSensitive; }

        public boolean matches(String input) {
            if (input == null) return false;
            String trimmed = input.trim();
            for (String answer : answers) {
                if (caseSensitive) {
                    if (trimmed.equals(answer)) return true;
                } else {
                    if (trimmed.equalsIgnoreCase(answer)) return true;
                }
            }
            return false;
        }
    }
}