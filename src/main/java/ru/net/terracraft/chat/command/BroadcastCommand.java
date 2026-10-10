package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

public class BroadcastCommand {

    private final TerraCraftChatPlugin plugin;

    public BroadcastCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("broadcast")
                .withAliases("bc", "announce")
                .withPermission("terracraftchat.broadcast")
                .withArguments(new GreedyStringArgument("message"))
                .executes((sender, args) -> {
                    String message = (String) args.get("message");

                    Component formatted = plugin.getChatFormatter()
                            .formatBroadcast(message);

                    Bukkit.broadcast(formatted);

                    // Логирование
                    if (plugin.getChatLogger() != null) {
                        String senderName = sender instanceof Player p
                                ? p.getName()
                                : "CONSOLE";
                        plugin.getChatLogger().log("BROADCAST", senderName, null, message);
                    }
                })
                .register();
    }
}