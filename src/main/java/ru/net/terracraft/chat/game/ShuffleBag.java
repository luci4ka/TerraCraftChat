package ru.net.terracraft.chat.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Мешок: выдаёт элементы в случайном порядке, пока не кончатся.
 * Потом перемешивает заново.
 */
public final class ShuffleBag<T> {

    private final List<T> items;
    private final Random random;
    private List<T> bag;
    private int index = 0;

    public ShuffleBag(List<T> items) {
        this.items = new ArrayList<>(items);
        this.random = new Random();
        this.bag = new ArrayList<>(items);
        Collections.shuffle(this.bag, this.random);
    }

    public T next() {
        if (items.isEmpty()) {
            throw new IllegalStateException("ShuffleBag is empty");
        }
        if (index >= bag.size()) {
            bag = new ArrayList<>(items);
            Collections.shuffle(bag, random);
            index = 0;
        }
        return bag.get(index++);
    }
}