package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.util.MessageUtil;

public class ChatReloadCommand {

    private final TerraCraftChatPlugin plugin;

    public ChatReloadCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("chat")
                .withAliases("tcc")
                .withPermission("terracraftchat.admin")
                .withSubcommand(new CommandAPICommand("reload")
                        .executes((sender, args) -> {
                            if (plugin.reload()) {
                                MessageUtil.sendPrefixed(sender, "chat.config-reloaded");
                            } else {
                                MessageUtil.sendPrefixed(sender, "chat.config-reload-failed");
                            }
                        })
                )
                .register();
    }
}