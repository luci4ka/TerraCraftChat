package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.MessageUtil;
import ru.net.terracraft.chat.SocialSpyManager;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

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