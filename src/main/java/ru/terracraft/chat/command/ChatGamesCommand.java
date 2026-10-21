package ru.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.util.MessageUtil;

public class ChatGamesCommand {

    private final TerraCraftChatPlugin plugin;

    public ChatGamesCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("chatgames")
                .withAliases("cg", "игры")
                .withPermission("terracraftchat.admin")
                // /chatgames — статус
                .executes((sender, args) -> {
                    var mgr = plugin.getChatGameManager();
                    boolean disabled = mgr.isManuallyDisabled();
                    boolean active = mgr.hasActiveGame();

                    Component stateComp = Component.translatable(disabled
                            ? "chat.chatgames-state-off"
                            : "chat.chatgames-state-on");
                    Component activeComp = Component.translatable(active
                            ? "chat.chatgames-state-yes"
                            : "chat.chatgames-state-no");

                    sender.sendMessage(Component.translatable(
                            "chat.chatgames-status",
                            Argument.component("state", stateComp),
                            Argument.component("active", activeComp)
                    ));
                })
                // /chatgames toggle
                .withSubcommand(new CommandAPICommand("toggle")
                        .executes((sender, args) -> {
                            boolean nowOn = plugin.getChatGameManager().toggle();
                            MessageUtil.sendPrefixed(sender,
                                    nowOn ? "chat.chatgames-on" : "chat.chatgames-off");
                        })
                )
                // /chatgames on
                .withSubcommand(new CommandAPICommand("on")
                        .executes((sender, args) -> {
                            if (!plugin.getChatGameManager().isManuallyDisabled()) {
                                MessageUtil.sendPrefixed(sender, "chat.chatgames-already-on");
                                return;
                            }
                            plugin.getChatGameManager().toggle();
                            MessageUtil.sendPrefixed(sender, "chat.chatgames-on");
                        })
                )
                // /chatgames off
                .withSubcommand(new CommandAPICommand("off")
                        .executes((sender, args) -> {
                            if (plugin.getChatGameManager().isManuallyDisabled()) {
                                MessageUtil.sendPrefixed(sender, "chat.chatgames-already-off");
                                return;
                            }
                            plugin.getChatGameManager().toggle();
                            MessageUtil.sendPrefixed(sender, "chat.chatgames-off");
                        })
                )
                .register();
    }
}