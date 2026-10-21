package ru.terracraft.chat.moderation;

import java.util.regex.Pattern;

/**
 * Одно правило модерации: regex → действие (команда).
 */
public final class Rule {

    private final String id;
    private final Pattern pattern;
    private final String command;      // "mute %player% 30m Реклама"
    private final String reason;
    private final boolean cancelMessage; // отменять ли сообщение (true — блок)

    public Rule(String id, Pattern pattern, String command, String reason, boolean cancelMessage) {
        this.id = id;
        this.pattern = pattern;
        this.command = command;
        this.reason = reason;
        this.cancelMessage = cancelMessage;
    }

    public String id() { return id; }
    public Pattern pattern() { return pattern; }
    public String command() { return command; }
    public String reason() { return reason; }
    public boolean cancelMessage() { return cancelMessage; }

    /**
     * Подставляет %player% в команду.
     */
    public String buildCommand(String playerName) {
        return command.replace("%player%", playerName);
    }
}