package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.MessageUtil;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

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
                        return;
                    }

                    if (plugin.getChatMessageRegistry().get(id).isEmpty()) {
                        return;
                    }

                    plugin.getThreadManager().setActive(sender, id);
                    MessageUtil.sendPrefixed(sender, "chat.thread-active");
                })
                .register();
    }
}