package ru.terracraft.chat.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.Tasks;

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
                            PlayerProfile profile = firstProfile(args.get("target"));

                            UUID targetId;
                            String targetName;
                            if (profile != null) {
                                targetId = profile.getId();
                                targetName = profile.getName() != null
                                        ? profile.getName() : targetId.toString();
                            } else {
                                targetId = sender.getUniqueId();
                                targetName = sender.getName();
                            }

                            // Запрос к БД — на виртуальном потоке, ответ — в main
                            Tasks.supply(plugin, "stats", () -> plugin.getStorageManager().getPlayerStats(targetId))
                                    .thenAccept(stats -> Tasks.sync(plugin, () -> {
                                        if (!sender.isOnline()) return;
                                        sender.sendMessage(format(targetName, stats));
                                    }));
                        })
                )
                .register();
    }

    private static Component format(String targetName, StorageManager.PlayerStats stats) {
        return Component.translatable("chat.stats-header",
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
    }

    private static PlayerProfile firstProfile(Object raw) {
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof PlayerProfile profile) {
            return profile;
        }
        return null;
    }
}
