package ru.terracraft.chat.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.MessageUtil;
import ru.terracraft.chat.util.Tasks;

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
                            List<PlayerProfile> profiles = asProfiles(args.get("target"));
                            if (profiles.isEmpty()) return;

                            PlayerProfile profile = profiles.get(0);
                            UUID targetId = profile.getId();
                            String name = profile.getName() != null
                                    ? profile.getName() : targetId.toString();

                            int limit = intArg(args.get("limit"), 50);
                            int page = intArg(args.get("page"), 1);

                            // Выборка истории может быть тяжёлой (до 500 строк) —
                            // только вне main-потока.
                            Tasks.supply(plugin, "history",
                                            () -> plugin.getStorageManager().loadHistoryBySender(targetId, limit))
                                    .thenAccept(rows -> Tasks.sync(plugin, () -> {
                                        if (!sender.isOnline() || rows == null) return;
                                        if (rows.isEmpty()) {
                                            MessageUtil.sendPrefixed(sender, "chat.history-empty",
                                                    Argument.string("player", name));
                                            return;
                                        }
                                        sendPage(sender, name, rows, page);
                                    }));
                        })
                )
                .register();
    }

    private void sendPage(net.kyori.adventure.audience.Audience sender, String name,
                          List<StorageManager.HistoryRow> rows, int page) {
        int totalPages = (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int current = Math.min(Math.max(page, 1), totalPages);
        int from = (current - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, rows.size());

        sender.sendMessage(Component.translatable(
                "chat.history-header",
                Argument.string("player", name),
                Argument.string("count", String.valueOf(rows.size())),
                Argument.string("page", String.valueOf(current)),
                Argument.string("pages", String.valueOf(totalPages))
        ));

        for (int i = from; i < to; i++) {
            StorageManager.HistoryRow row = rows.get(i);
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

        if (totalPages > 1) {
            int nextPage = current < totalPages ? current + 1 : 1;
            sender.sendMessage(Component.translatable(
                    "chat.history-footer",
                    Argument.string("player", name),
                    Argument.string("page", String.valueOf(nextPage))
            ));
        }
    }

    private static int intArg(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private static List<PlayerProfile> asProfiles(Object raw) {
        if (raw instanceof List<?> list) {
            return (List<PlayerProfile>) list;
        }
        return List.of();
    }
}
