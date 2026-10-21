package ru.terracraft.chat.format;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/**
 * Безопасный парсер ников: только цвета и форматирование.
 * Клики, hover, insertion, font, translation, selector, score — запрещены.
 */
public final class NickFormat {

    private NickFormat() {}

    public static final MiniMessage SAFE = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolvers(
                            StandardTags.color(),
                            StandardTags.decorations(),
                            StandardTags.gradient(),
                            StandardTags.rainbow(),
                            StandardTags.reset()
                    )
                    .build())
            .build();
}