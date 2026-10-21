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
                        // /chat reload  — всё
                        .executes((sender, args) -> {
                            if (plugin.reload()) {
                                MessageUtil.sendPrefixed(sender, "chat.config-reloaded");
                            } else {
                                MessageUtil.sendPrefixed(sender, "chat.config-reload-failed");
                            }
                        })
                        // /chat reload rules
                        .withSubcommand(new CommandAPICommand("rules")
                                .executes((sender, args) -> {
                                    plugin.getRuleManager().reload();
                                    MessageUtil.sendPrefixed(sender, "chat.rules-reloaded");
                                })
                        )
                        // /chat reload games
                        .withSubcommand(new CommandAPICommand("games")
                                .executes((sender, args) -> {
                                    plugin.getGamesConfig().load();
                                    plugin.getChatGameManager().stop();
                                    plugin.getChatGameManager().start();
                                    MessageUtil.sendPrefixed(sender, "chat.games-reloaded");
                                })
                        )
                        // /chat reload emojis
                        .withSubcommand(new CommandAPICommand("emojis")
                                .executes((sender, args) -> {
                                    plugin.getEmojiManager().load();
                                    MessageUtil.sendPrefixed(sender, "chat.emojis-reloaded");
                                })
                        )
                        // /chat reload lang
                        .withSubcommand(new CommandAPICommand("lang")
                                .executes((sender, args) -> {
                                    plugin.getTranslationManager().reload();
                                    MessageUtil.sendPrefixed(sender, "chat.lang-reloaded");
                                })
                        )
                )
                .register();
    }
}