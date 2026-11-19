package ru.terracraft.chat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LevenshteinUtil — расстояние и схожесть")
class LevenshteinUtilTest {

    @ParameterizedTest(name = "distance(\"{0}\", \"{1}\") = {2}")
    @CsvSource({
            "kitten, sitting, 3",
            "flaw, lawn, 2",
            "'', abc, 3",
            "abc, '', 3",
            "abc, abc, 0",
            "привет, привот, 1"
    })
    void computesDistance(String left, String right, int expected) {
        assertEquals(expected, LevenshteinUtil.distance(left, right));
    }

    @Test
    @DisplayName("симметричность расстояния")
    void distanceIsSymmetric() {
        String[][] samples = {
                {"привет мир", "превет мир"},
                {"hello", "helo"},
                {"a", "abcdef"}
        };

        for (String[] pair : samples) {
            assertEquals(
                    LevenshteinUtil.distance(pair[0], pair[1]),
                    LevenshteinUtil.distance(pair[1], pair[0]),
                    "расстояние должно быть симметричным для " + pair[0] + "/" + pair[1]
            );
        }
    }

    @Test
    void identicalStringsAreFullySimilar() {
        assertAll(
                () -> assertEquals(1.0, LevenshteinUtil.similarity("привет", "ПРИВЕТ")),
                () -> assertEquals(1.0, LevenshteinUtil.similarity("Hello", "hello"))
        );
    }

    @Test
    void nullAndEmptyAreNotSimilar() {
        assertAll(
                () -> assertEquals(0.0, LevenshteinUtil.similarity(null, "abc")),
                () -> assertEquals(0.0, LevenshteinUtil.similarity("abc", null)),
                () -> assertEquals(0.0, LevenshteinUtil.similarity("", "abc")),
                () -> assertEquals(0.0, LevenshteinUtil.similarity("abc", ""))
        );
    }

    @ParameterizedTest(name = "\"{0}\" и \"{1}\" похожи минимум на 0.8")
    @CsvSource({
            "привет мир, привет мир!",
            "как дела, как дeла",
            "тест сообщения, тест сообщение"
    })
    void similarStringsScoreHigh(String left, String right) {
        assertTrue(LevenshteinUtil.similarity(left, right) >= 0.8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"совсем другое сообщение", "abcdefgh", "1234567890"})
    void unrelatedStringsScoreLow(String other) {
        assertTrue(LevenshteinUtil.similarity("привет мир", other) < 0.6);
    }
}
