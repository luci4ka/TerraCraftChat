package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.ChatMessage;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

import java.util.Optional;
import java.util.UUID;

public class DeleteCommand {

    private final TerraCraftChatPlugin plugin;

    public DeleteCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("chat_delete")
                .withPermission("terracraftchat.moderator")
                .withArguments(new StringArgument("id"))
                .executesPlayer((sender, args) -> {
                    String idStr = (String) args.get("id");
                    UUID id;
                    try {
                        id = UUID.fromString(idStr);
                    } catch (IllegalArgumentException ex) {
                        return;
                    }

                    Optional<ChatMessage> opt = plugin.getChatMessageRegistry().get(id);
                    if (opt.isEmpty()) return;

                    ChatMessage msg = opt.get();

                    // Уведомление о удалении — переведено
                    Component notice = Component.translatable("chat.delete-notice",
                            Argument.string("sender", msg.getSenderName()),
                            Argument.string("moderator", sender.getName()));

                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (msg.wasSeenBy(viewer.getUniqueId())) {
                            viewer.sendMessage(Component.empty());
                            viewer.sendMessage(notice);
                        }
                    }

                    // Убираем из реестра
                    plugin.getChatMessageRegistry().remove(id);

                    // Логируем (лог — без перевода)
                    if (plugin.getChatLogger() != null) {
                        plugin.getChatLogger().log("DELETE", sender.getName(),
                                null, "Deleted message from " + msg.getSenderName()
                                        + ": " + msg.getRawMessage());
                    }
                })
                .register();
    }
}