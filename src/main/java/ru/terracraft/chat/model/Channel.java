package ru.terracraft.chat.model;

import java.util.Locale;

/**
 * Описание одного канала чата (создаётся из конфига, иммутабелен).
 *
 * @param id            "global", "local", "staff"
 * @param prefix        "!", "", "#"
 * @param range         радиус в блоках, -1 = без ограничения
 * @param permission    "" = доступно всем
 * @param formatKey     ключ в {@code config.formats()}, напр. "global"
 * @param cooldown      сек, 0 = наследовать общий
 * @param publicChannel true = Discord, бот, упоминания
 */
public record Channel(String id, String prefix, int range, String permission,
                      String formatKey, int cooldown, boolean publicChannel) {

    public Channel {
        id = id == null ? "" : id.toLowerCase(Locale.ROOT);
        prefix = prefix == null ? "" : prefix;
        permission = permission == null ? "" : permission;
    }

    public boolean isGlobal() { return range < 0; }

    public boolean hasPrefix() { return !prefix.isEmpty(); }

    /**
     * Публичный канал: сообщения идут в Discord, LocalAnswerManager,
     * notifyMentions. Канал с правом (permission не пустой) НИКОГДА
     * не публичный — даже если в конфиге public: true.
     * Это защита от утечки staff-канала.
     */
    public boolean isPublic() {
        return publicChannel && permission.isEmpty();
    }

    /**
     * Проверяет, начинается ли сообщение с префикса канала.
     * Возвращает текст без префикса или null, если не совпало.
     */
    public String stripPrefix(String message) {
        if (!hasPrefix()) return null;
        if (!message.startsWith(prefix)) return null;
        return message.substring(prefix.length()).trim();
    }
}
