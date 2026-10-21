package ru.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import net.kyori.adventure.text.minimessage.translation.Argument;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.util.MessageUtil;

public class ChatPurgeCommand {

    private final TerraCraftChatPlugin plugin;

    public ChatPurgeCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("chat")
                .withSubcommand(new CommandAPICommand("purge")
                        .withPermission("terracraftchat.admin")
                        .withArguments(new IntegerArgument("days", 1, 3650))
                        .executes((sender, args) -> {
                            int days = (int) args.get("days");
                            long cutoff = System.currentTimeMillis()
                                    - (days * 86_400_000L);

                            plugin.getStorageManager().cleanupHistory(cutoff);

                            MessageUtil.sendPrefixed(sender, "chat.purge-started",
                                    Argument.string("days", String.valueOf(days)));
                        })
                )
                .register();
    }
}