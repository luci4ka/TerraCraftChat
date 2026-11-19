package ru.terracraft.chat.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ShuffleBag — мешок без повторов")
class ShuffleBagTest {

    @Test
    @DisplayName("за один проход выдаёт все элементы ровно по разу")
    @RepeatedTest(20)
    void yieldsEveryItemOnce() {
        ShuffleBag<String> bag = new ShuffleBag<>(List.of("a", "b", "c", "d"));

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            seen.add(bag.next());
        }

        assertEquals(Set.of("a", "b", "c", "d"), seen);
    }

    @Test
    @DisplayName("после опустошения мешок перемешивается заново")
    void refillsAfterExhaustion() {
        ShuffleBag<Integer> bag = new ShuffleBag<>(List.of(1, 2, 3));

        for (int i = 0; i < 3; i++) {
            assertTrue(bag.next() >= 1);
        }

        Set<Integer> secondRound = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            secondRound.add(bag.next());
        }

        assertEquals(Set.of(1, 2, 3), secondRound);
    }

    @Test
    void emptyBagFailsLoudly() {
        assertThrows(IllegalStateException.class, () -> new ShuffleBag<String>(List.of()).next());
    }
}
