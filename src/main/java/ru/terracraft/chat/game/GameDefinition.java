package ru.terracraft.chat.game;

import java.util.List;

/**
 * @param words      Для unscramble
 * @param questions  Для quiz
 * @param operations Для math
 * @param timeoutSeconds Время на ответ (сек), 0 = глобальный
 */
public record GameDefinition(String id, GameType type, int weight, List<String> rewards, List<String> words,
                             List<Question> questions, List<String> operations, int minNumber, int maxNumber,
                             int timeoutSeconds) {

    public record Question(String question, List<String> answers, boolean caseSensitive) {

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