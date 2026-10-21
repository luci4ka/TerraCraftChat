package ru.net.terracraft.chat.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.manager.IgnoreManager;
import ru.net.terracraft.chat.util.MessageUtil;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class IgnoreCommand {

    private final TerraCraftChatPlugin plugin;

    public IgnoreCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("ignore")
                .withPermission("terracraftchat.ignore")
                .withSubcommand(new CommandAPICommand("list")
                        .executesPlayer((sender, args) -> {
                            IgnoreManager im = plugin.getIgnoreManager();
                            Set<UUID> ignored = im.getIgnoredByUuid(sender.getUniqueId());

                            if (ignored.isEmpty()) {
                                MessageUtil.sendPrefixed(sender, "chat.ignore-list-empty");
                                return;
                            }

                            sender.sendMessage(Component.translatable("chat.ignore-list-header",
                                    Argument.string("count", String.valueOf(ignored.size()))));

                            for (UUID uuid : ignored) {
                                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                                String name = op.getName() != null
                                        ? op.getName() : uuid.toString();
                                sender.sendMessage(Component.translatable("chat.ignore-list-entry",
                                        Argument.string("player", name)));
                            }
                        })
                )
                .withArguments(
                        new PlayerProfileArgument("target"),
                        new StringArgument("time").setOptional(true)
                )
                .executesPlayer((sender, args) -> {
                    List<PlayerProfile> profiles =
                            (List<PlayerProfile>) args.get("target");
                    if (profiles == null || profiles.isEmpty()) return;

                    PlayerProfile profile = profiles.get(0);
                    UUID targetId = profile.getUniqueId();
                    String rawName = profile.getName();
                    String name = rawName != null ? rawName : targetId.toString();

                    if (targetId.equals(sender.getUniqueId())) {
                        MessageUtil.sendPrefixed(sender, "chat.cannot-ignore-self");
                        return;
                    }

                    IgnoreManager im = plugin.getIgnoreManager();

                    if (im.isIgnoring(sender.getUniqueId(), targetId)) {
                        im.removeIgnore(sender, targetId);
                        MessageUtil.sendPrefixed(sender, "chat.ignore-disabled",
                                Argument.string("player", name));
                        return;
                    }

                    String timeStr = (String) args.get("time");
                    long ttlMs = parseTime(timeStr);
                    if (ttlMs < 0) {
                        MessageUtil.sendPrefixed(sender, "chat.ignore-invalid-time");
                        return;
                    }

                    im.addIgnore(sender, targetId, ttlMs);

                    if (ttlMs > 0) {
                        long minutes = ttlMs / 60_000;
                        MessageUtil.sendPrefixed(sender, "chat.ignore-enabled-temp",
                                Argument.string("player", name),
                                Argument.string("time", String.valueOf(minutes)));
                    } else {
                        MessageUtil.sendPrefixed(sender, "chat.ignore-enabled",
                                Argument.string("player", name));
                    }
                })
                .register();
    }

    private long parseTime(String str) {
        if (str == null || str.isBlank()) return 0L;
        str = str.toLowerCase().trim();
        try {
            char unit = str.charAt(str.length() - 1);
            long value = Long.parseLong(str.substring(0, str.length() - 1));
            if (value <= 0) return -1L;
            return switch (unit) {
                case 's' -> value * 1000L;
                case 'm' -> value * 60_000L;
                case 'h' -> value * 3_600_000L;
                case 'd' -> value * 86_400_000L;
                case 'w' -> value * 604_800_000L;   // ← добавлено: недели
                default -> -1L;
            };
        } catch (Exception e) {
            return -1L;
        }
    }
}