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

    private static final int PAGE_SIZE = 10;

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
                        .withOptionalArguments(
                                new IntegerArgument("limit", 1, 500),
                                new IntegerArgument("page", 1, 1000)
                        )
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

                            Object pageRaw = args.get("page");
                            int page = pageRaw != null
                                    ? ((Number) pageRaw).intValue() : 1;

                            var rows = plugin.getStorageManager()
                                    .loadHistoryBySender(targetId, limit);

                            if (rows.isEmpty()) {
                                MessageUtil.sendPrefixed(sender, "chat.history-empty",
                                        Argument.string("player", name));
                                return;
                            }

                            int totalPages = (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE;
                            if (page > totalPages) page = totalPages;
                            int from = (page - 1) * PAGE_SIZE;
                            int to = Math.min(from + PAGE_SIZE, rows.size());

                            sender.sendMessage(Component.translatable(
                                    "chat.history-header",
                                    Argument.string("player", name),
                                    Argument.string("count", String.valueOf(rows.size())),
                                    Argument.string("page", String.valueOf(page)),
                                    Argument.string("pages", String.valueOf(totalPages))
                            ));

                            for (int i = from; i < to; i++) {
                                var row = rows.get(i);
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

                            // Футер пагинации — с подстановкой игрока и номера страницы
                            if (totalPages > 1) {
                                int nextPage = page < totalPages ? page + 1 : 1;
                                sender.sendMessage(Component.translatable(
                                        "chat.history-footer",
                                        Argument.string("player", name),
                                        Argument.string("page", String.valueOf(nextPage))
                                ));
                            }
                        })
                )
                .register();
    }
}