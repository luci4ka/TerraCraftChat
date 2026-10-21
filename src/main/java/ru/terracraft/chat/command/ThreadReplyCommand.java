package ru.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.minimessage.translation.Argument;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.model.ChatMessage;
import ru.terracraft.chat.util.MessageUtil;

import java.util.Optional;
import java.util.UUID;

public class ThreadReplyCommand {

    private final TerraCraftChatPlugin plugin;

    public ThreadReplyCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("thread_reply")
                .withPermission("terracraftchat.thread")
                .withArguments(new StringArgument("id"))
                .executesPlayer((sender, args) -> {
                    String idStr = (String) args.get("id");

                    UUID id;
                    try {
                        id = UUID.fromString(idStr);
                    } catch (IllegalArgumentException ex) {
                        MessageUtil.sendPrefixed(sender, "chat.invalid-id");
                        return;
                    }

                    Optional<ChatMessage> opt = plugin.getChatMessageRegistry().get(id);
                    if (opt.isEmpty()) {
                        MessageUtil.sendPrefixed(sender, "chat.message-not-found");
                        return;
                    }

                    ChatMessage parent = opt.get();

                    // Запрет отвечать на своё сообщение
                    if (parent.getSenderUuid().equals(sender.getUniqueId())) {
                        MessageUtil.sendPrefixed(sender, "chat.thread-self-reply",
                                Argument.string("player", parent.getSenderName()));
                        return;
                    }

                    plugin.getThreadManager().setActive(sender, id);
                    MessageUtil.sendPrefixed(sender, "chat.thread-active");
                })
                .register();
    }
}