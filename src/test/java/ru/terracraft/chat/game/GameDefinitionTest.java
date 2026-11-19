package ru.terracraft.chat.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GameDefinition — проверка ответов викторины")
class GameDefinitionTest {

    private static GameDefinition.Question quizQuestion(boolean caseSensitive) {
        return new GameDefinition.Question("Сколько блоков в стаке?", List.of("64", "шестьдесят четыре"), caseSensitive);
    }

    @Test
    @DisplayName("по умолчанию регистр не важен")
    void answerIsCaseInsensitiveByDefault() {
        GameDefinition.Question question = quizQuestion(false);

        assertTrue(question.matches("64"));
        assertTrue(question.matches("Шестьдесят Четыре"));
        assertTrue(question.matches("  64  "), "пробелы по краям не должны мешать");
    }

    @Test
    @DisplayName("caseSensitive=true требует точного совпадения")
    void caseSensitiveAnswer() {
        GameDefinition.Question question = new GameDefinition.Question(
                "Столица?", List.of("Paris"), true);

        assertTrue(question.matches("Paris"));
        assertFalse(question.matches("paris"));
    }

    @Test
    void rejectsWrongAndNullAnswers() {
        GameDefinition.Question question = quizQuestion(false);

        assertFalse(question.matches("32"));
        assertFalse(question.matches(null));
        assertFalse(question.matches(""));
    }

    @Test
    @DisplayName("GameType содержит все поддерживаемые типы")
    void gameTypesAreKnown() {
        assertEquals(3, GameType.values().length);
        assertEquals(GameType.UNSCRAMBLE, GameType.valueOf("UNSCRAMBLE"));
    }
}
