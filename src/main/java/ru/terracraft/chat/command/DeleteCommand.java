package ru.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.model.ChatMessage;
import ru.terracraft.chat.util.MessageUtil;

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
                    if (!plugin.getChatConfig().delete().enabled()) {
                        MessageUtil.sendPrefixed(sender, "chat.delete-disabled");
                        return;
                    }

                    String idStr = (String) args.get("id");
                    UUID id;
                    try {
                        id = UUID.fromString(idStr);
                    } catch (IllegalArgumentException ex) {
                        MessageUtil.sendPrefixed(sender, "chat.invalid-id");
                        return;
                    }

                    Optional<ChatMessage> opt = plugin.getChatMessageRegistry().get(id);
                    if (opt.isEmpty()) {
                        MessageUtil.sendPrefixed(sender, "chat.message-not-found");
                        return;
                    }

                    ChatMessage msg = opt.get();

                    Component notice = Component.translatable("chat.delete-notice",
                            Argument.string("sender", msg.getSenderName()),
                            Argument.string("moderator", sender.getName()));

                    // Перерисовка — по 1 viewer за тик, чтобы не лагать main
                    // (при 50 viewers — 50 тиков = 2.5 сек, но main не лагает)
                    int delay = 1;
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (!msg.wasSeenBy(viewer.getUniqueId())) continue;

                        final Player v = viewer;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (!v.isOnline()) return;
                            v.sendMessage(notice);
                            plugin.getChatRedrawManager().redrawFor(v, id);
                        }, delay);
                        delay++;
                    }

                    // Удаляем из реестра + БД (async) — сразу
                    plugin.getChatMessageRegistry().remove(id);
                    plugin.getStorageManager().deleteMessage(id);

                    if (plugin.getChatLogger() != null) {
                        plugin.getChatLogger().log("DELETE", sender.getName(),
                                null, "Deleted (from " + msg.getSenderName()
                                        + "): " + msg.getRawMessage());
                    }
                })
                .register();
    }
}