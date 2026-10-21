package ru.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;

public class BroadcastCommand {

    private final TerraCraftChatPlugin plugin;

    public BroadcastCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("broadcast")
                .withAliases("bc", "announce", "tcbroadcast")
                .withPermission("terracraftchat.broadcast")
                .withArguments(new GreedyStringArgument("message"))
                .executes((sender, args) -> {
                    String message = (String) args.get("message");

                    Component formatted = plugin.getChatFormatter()
                            .formatBroadcast(message);

                    Bukkit.broadcast(formatted);

                    String senderName = sender instanceof Player p ? p.getName() : "CONSOLE";
                    String worldName = sender instanceof Player p ? p.getWorld().getName() : "server";

                    // Логирование
                    if (plugin.getChatLogger() != null) {
                        plugin.getChatLogger().log("BROADCAST", senderName, null, message);
                    }

                    // Discord — если включён
                    if (plugin.getChatConfig().discord().enabled()
                            && !plugin.getChatConfig().discord().webhookUrl().isEmpty()) {
                        String prefix = plugin.getChatConfig().discord().broadcastPrefix();
                        String plainForDiscord = prefix + " " + senderName + ": " + message;

                        plugin.getDiscordWebhook().queue(senderName, worldName, plainForDiscord);
                    }
                })
                .register();
    }
}