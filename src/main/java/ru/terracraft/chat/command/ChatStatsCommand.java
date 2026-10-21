package ru.terracraft.chat.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import ru.terracraft.chat.TerraCraftChatPlugin;

import java.util.List;
import java.util.UUID;

public class ChatStatsCommand {

    private final TerraCraftChatPlugin plugin;

    public ChatStatsCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("chat")
                .withSubcommand(new CommandAPICommand("stats")
                        .withPermission("terracraftchat.stats")
                        .withOptionalArguments(new PlayerProfileArgument("target"))
                        .executesPlayer((sender, args) -> {
                            PlayerProfile profile = null;
                            Object raw = args.get("target");
                            if (raw instanceof List<?> list && !list.isEmpty()) {
                                profile = (PlayerProfile) list.get(0);
                            }

                            UUID targetId;
                            String targetName;
                            if (profile != null) {
                                targetId = profile.getUniqueId();
                                targetName = profile.getName() != null
                                        ? profile.getName() : targetId.toString();
                            } else {
                                targetId = sender.getUniqueId();
                                targetName = sender.getName();
                            }

                            var stats = plugin.getStorageManager().getPlayerStats(targetId);

                            Component result = Component.translatable("chat.stats-header",
                                            Argument.string("player", targetName))
                                    .append(Component.newline())
                                    .append(Component.translatable("chat.stats-sent",
                                            Argument.string("count", String.valueOf(stats.messagesSent()))))
                                    .append(Component.newline())
                                    .append(Component.translatable("chat.stats-blocked",
                                            Argument.string("count", String.valueOf(stats.messagesBlocked()))))
                                    .append(Component.newline())
                                    .append(Component.translatable("chat.stats-swear",
                                            Argument.string("count", String.valueOf(stats.swearCaught()))))
                                    .append(Component.newline())
                                    .append(Component.translatable("chat.stats-ads",
                                            Argument.string("count", String.valueOf(stats.adsCaught()))));

                            sender.sendMessage(result);
                        })
                )
                .register();
    }
}