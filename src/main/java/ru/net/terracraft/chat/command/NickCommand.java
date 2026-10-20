package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.format.NickFormat;
import ru.net.terracraft.chat.util.MessageUtil;

import java.util.Locale;
import java.util.Set;

public class NickCommand {

    private static final int MIN_VISIBLE_LENGTH = 3;
    private static final int MAX_VISIBLE_LENGTH = 16;
    private static final int MAX_RAW_LENGTH = 64;

    // Зарезервированные имена — нельзя занимать (латиница + кириллица)
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "console", "server", "staff",
            "moderator", "mod", "owner", "helper", "builder", "dev",
            "админ", "администратор", "консоль", "сервер", "стафф",
            "модератор", "мод", "владелец", "хелпер", "билдер", "разраб"
    );

    private final TerraCraftChatPlugin plugin;

    public NickCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("nick")
                .withPermission("terracraftchat.nick")
                .withArguments(new GreedyStringArgument("nickname"))
                .executesPlayer((sender, args) -> {
                    String raw = ((String) args.get("nickname")).trim();

                    if (raw.isEmpty()) {
                        MessageUtil.sendPrefixed(sender, "chat.nick-empty");
                        return;
                    }
                    if (raw.length() > MAX_RAW_LENGTH) {
                        MessageUtil.sendPrefixed(sender, "chat.nick-too-long");
                        return;
                    }

                    // Парсинг безопасным парсером
                    String visible;
                    try {
                        visible = PlainTextComponentSerializer.plainText().serialize(
                                NickFormat.SAFE.deserialize(raw)
                        );
                    } catch (Exception ex) {
                        MessageUtil.sendPrefixed(sender, "chat.nick-invalid");
                        return;
                    }

                    // Длина видимого текста
                    if (visible.isBlank()) {
                        MessageUtil.sendPrefixed(sender, "chat.nick-empty");
                        return;
                    }
                    if (visible.length() < MIN_VISIBLE_LENGTH
                            || visible.length() > MAX_VISIBLE_LENGTH) {
                        MessageUtil.sendPrefixed(sender, "chat.nick-too-long");
                        return;
                    }

                    String lower = visible.toLowerCase(Locale.ROOT);

                    // Зарезервированные имена
                    if (RESERVED.contains(lower)) {
                        MessageUtil.sendPrefixed(sender, "chat.nick-reserved",
                                Argument.string("player", visible));
                        return;
                    }

                    // Имперсонация: реальные имена онлайн-игроков
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.equals(sender)) continue;
                        if (online.getName().toLowerCase(Locale.ROOT).equals(lower)) {
                            MessageUtil.sendPrefixed(sender, "chat.nick-impersonation",
                                    Argument.string("player", online.getName()));
                            return;
                        }
                    }

                    // Имперсонация: чужие ники (онлайн)
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.equals(sender)) continue;
                        String otherNick = plugin.getNickManager().getNick(online);
                        if (otherNick == null) continue;

                        String otherVisible;
                        try {
                            otherVisible = PlainTextComponentSerializer.plainText().serialize(
                                    NickFormat.SAFE.deserialize(otherNick)
                            );
                        } catch (Exception ex) {
                            continue;
                        }

                        if (otherVisible.toLowerCase(Locale.ROOT).equals(lower)) {
                            MessageUtil.sendPrefixed(sender, "chat.nick-impersonation",
                                    Argument.string("player", online.getName()));
                            return;
                        }
                    }

                    // Имперсонация: имена офлайн-игроков (только из кэша — не блокирует)
                    OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(visible);
                    if (offline != null
                            && !offline.getUniqueId().equals(sender.getUniqueId())
                            && offline.hasPlayedBefore()) {
                        MessageUtil.sendPrefixed(sender, "chat.nick-impersonation",
                                Argument.string("player", visible));
                        return;
                    }

                    plugin.getNickManager().setNick(sender, raw);
                    MessageUtil.sendPrefixed(sender, "chat.nick-set");
                })
                .register();

        new CommandAPICommand("nickreset")
                .withPermission("terracraftchat.nick")
                .executesPlayer((sender, args) -> {
                    plugin.getNickManager().resetNick(sender);
                    MessageUtil.sendPrefixed(sender, "chat.nick-reset");
                })
                .register();
    }
}