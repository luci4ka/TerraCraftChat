package ru.terracraft.chat.model;

import java.util.Locale;

/**
 * Описание одного канала чата.
 * Создаётся из конфига, иммутабелен.
 */
public final class Channel {

    private final String id;              // "global", "local", "staff"
    private final String prefix;          // "!", "", "#"
    private final int range;              // -1 = без ограничения
    private final String permission;      // "" = доступно всем
    private final String formatKey;       // ключ в config.formats(), напр. "global"
    private final int cooldown;           // сек, 0 = наследовать общий
    private final boolean publicChannel;  // true = Discord, бот, упоминания

    public Channel(String id, String prefix, int range,
                   String permission, String formatKey, int cooldown,
                   boolean publicChannel) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.prefix = prefix == null ? "" : prefix;
        this.range = range;
        this.permission = permission == null ? "" : permission;
        this.formatKey = formatKey;
        this.cooldown = cooldown;
        this.publicChannel = publicChannel;
    }

    public String id() { return id; }
    public String prefix() { return prefix; }
    public int range() { return range; }
    public String permission() { return permission; }
    public String formatKey() { return formatKey; }
    public int cooldown() { return cooldown; }

    public boolean isGlobal() { return range < 0; }
    public boolean hasPrefix() { return !prefix.isEmpty(); }

    /**
     * Публичный канал: сообщения идут в Discord, LocalAnswerManager,
     * notifyMentions. Канал с правом (permission не пустой) НИКОГДА
     * не публичный — даже если в конфиге public: true.
     * Это защита от утечки staff-канала.
     */
    public boolean isPublic() {
        return publicChannel && (permission == null || permission.isEmpty());
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