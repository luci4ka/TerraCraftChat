package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.IgnoreManager;
import ru.net.terracraft.chat.MessageUtil;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

public class IgnoreCommand {

    private final TerraCraftChatPlugin plugin;

    public IgnoreCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("ignore")
                .withPermission("terracraftchat.ignore")
                .withArguments(new EntitySelectorArgument.OnePlayer("target"))
                .executesPlayer((sender, args) -> {
                    Player target = (Player) args.get("target");

                    if (sender.getUniqueId().equals(target.getUniqueId())) {
                        MessageUtil.sendPrefixed(sender, "chat.cannot-ignore-self");
                        return;
                    }

                    IgnoreManager ignoreManager = plugin.getIgnoreManager();

                    if (ignoreManager.isIgnoring(sender, target)) {
                        ignoreManager.removeIgnore(sender, target);
                        MessageUtil.sendPrefixed(sender, "chat.ignore-disabled",
                                Argument.string("player", target.getName()));
                    } else {
                        ignoreManager.addIgnore(sender, target);
                        MessageUtil.sendPrefixed(sender, "chat.ignore-enabled",
                                Argument.string("player", target.getName()));
                    }
                })
                .register();
    }
}