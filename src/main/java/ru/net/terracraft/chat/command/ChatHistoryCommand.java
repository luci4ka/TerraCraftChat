package ru.net.terracraft.chat.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.util.MessageUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class ChatHistoryCommand {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());

    private final TerraCraftChatPlugin plugin;

    public ChatHistoryCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("chat")
                .withSubcommand(new CommandAPICommand("history")
                        .withPermission("terracraftchat.admin")
                        .withArguments(new PlayerProfileArgument("target"))
                        .withOptionalArguments(new IntegerArgument("limit", 1, 500))
                        .executesPlayer((sender, args) -> {
                            List<PlayerProfile> profiles =
                                    (List<PlayerProfile>) args.get("target");
                            if (profiles == null || profiles.isEmpty()) return;

                            PlayerProfile profile = profiles.get(0);
                            UUID targetId = profile.getUniqueId();
                            String name = profile.getName() != null
                                    ? profile.getName() : targetId.toString();

                            Object limitRaw = args.get("limit");
                            int limit = limitRaw != null
                                    ? ((Number) limitRaw).intValue() : 50;

                            var rows = plugin.getStorageManager()
                                    .loadHistoryBySender(targetId, limit);

                            if (rows.isEmpty()) {
                                MessageUtil.sendPrefixed(sender, "chat.history-empty",
                                        Argument.string("player", name));
                                return;
                            }

                            sender.sendMessage(Component.translatable(
                                    "chat.history-header",
                                    Argument.string("player", name),
                                    Argument.string("count", String.valueOf(rows.size()))
                            ));

                            for (var row : rows) {
                                String time = TIME_FMT.format(Instant.ofEpochMilli(row.timestamp()));
                                String date = DATE_FMT.format(Instant.ofEpochMilli(row.timestamp()));

                                sender.sendMessage(Component.translatable(
                                        "chat.history-entry",
                                        Argument.string("date", date),
                                        Argument.string("time", time),
                                        Argument.string("channel", row.channelId()),
                                        Argument.string("message", row.rawMessage())
                                ));
                            }
                        })
                )
                .register();
    }
}