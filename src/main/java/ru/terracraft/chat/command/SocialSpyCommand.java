package ru.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import ru.terracraft.chat.util.MessageUtil;
import ru.terracraft.chat.manager.SocialSpyManager;
import ru.terracraft.chat.TerraCraftChatPlugin;

public class SocialSpyCommand {

    private final TerraCraftChatPlugin plugin;

    public SocialSpyCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("socialspy")
                .withAliases("spy")
                .withPermission("terracraftchat.socialspy")
                .executesPlayer((sender, args) -> {
                    SocialSpyManager spy = plugin.getSocialSpyManager();
                    boolean nowEnabled = spy.toggle(sender);

                    MessageUtil.sendPrefixed(sender,
                            nowEnabled ? "chat.socialspy-enabled" : "chat.socialspy-disabled");
                })
                .register();
    }
}