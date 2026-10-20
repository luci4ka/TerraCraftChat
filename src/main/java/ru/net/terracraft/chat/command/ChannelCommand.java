package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.model.Channel;
import ru.net.terracraft.chat.util.MessageUtil;

public class ChannelCommand {

    private final TerraCraftChatPlugin plugin;

    public ChannelCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("channel")
                // /channel reset
                .withSubcommand(new CommandAPICommand("reset")
                        .executesPlayer((sender, args) -> {
                            plugin.getChannelManager().clearActive(sender);
                            MessageUtil.sendPrefixed(sender, "chat.channel-reset");
                        })
                )
                // /channel <id>
                .withArguments(new StringArgument("id")
                        .replaceSuggestions((info, builder) -> {
                            if (info.sender() instanceof Player p) {
                                for (Channel ch : plugin.getChannelManager().getAvailable(p)) {
                                    builder.suggest(ch.id());
                                }
                            }
                            return builder.buildFuture();
                        })
                )
                .executesPlayer((sender, args) -> {
                    String id = (String) args.get("id");
                    Channel ch = plugin.getChannelManager().get(id);

                    if (ch == null) {
                        MessageUtil.sendPrefixed(sender, "chat.channel-unknown",
                                Argument.string("id", id));
                        return;
                    }
                    if (!plugin.getChannelManager().canUse(sender, ch)) {
                        MessageUtil.sendPrefixed(sender, "chat.channel-no-access");
                        return;
                    }

                    plugin.getChannelManager().setActive(sender, ch);
                    MessageUtil.sendPrefixed(sender, "chat.channel-switched",
                            Argument.string("channel", ch.id()));
                })
                .register();
    }
}