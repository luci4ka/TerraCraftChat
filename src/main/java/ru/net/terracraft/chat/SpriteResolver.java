package ru.net.terracraft.chat;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpriteResolver {

    // Короткие алиасы → полные спрайт-теги
    private static final Map<String, String> ALIASES = Map.of(
            "diamond", "<sprite:items:item/diamond>",
            "emerald", "<sprite:items:item/emerald>",
            "gold", "<sprite:items:item/gold_ingot>",
            "iron", "<sprite:items:item/iron_ingot>",
            "netherite", "<sprite:items:item/netherite_ingot>",
            "heart", "<sprite:gui:hud/heart/full>",
            "hunger", "<sprite:gui:hud/food_full>",
            "xp", "<sprite:gui:hud/experience_bar_background>"
    );

    // Паттерн для :alias: (только буквы и подчёркивания)
    private static final Pattern SHORTCODE = Pattern.compile(":([a-z_]+):");

    /**
     * Заменяет короткие теги на полные sprite-теги.
     * Например: ":diamond:" → "<sprite:items:item/diamond>"
     */
    public static String resolve(String input) {
        if (input == null || !input.contains(":")) {
            return input;
        }

        Matcher matcher = SHORTCODE.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String alias = matcher.group(1);
            String replacement = ALIASES.getOrDefault(alias, matcher.group(0)); // если нет алиаса — оставляем как есть
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Проверяет, есть ли в строке sprite-теги (полные).
     */
    public static boolean hasSpriteTags(String input) {
        return input != null && input.contains("<sprite:");
    }
}