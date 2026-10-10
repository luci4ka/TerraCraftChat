package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.IgnoreManager;
import ru.net.terracraft.chat.MessageUtil;
import ru.net.terracraft.chat.ReplyManager;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

public class ReplyCommand {

    private final TerraCraftChatPlugin plugin;

    public ReplyCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("reply")
                .withAliases("r")
                .withPermission("terracraftchat.reply")
                .withArguments(new GreedyStringArgument("message"))
                .executesPlayer((sender, args) -> {
                    String message = (String) args.get("message");

                    ReplyManager replyManager = plugin.getReplyManager();
                    Player target = replyManager.getLastReply(sender);

                    if (target == null || !target.isOnline()) {
                        MessageUtil.sendPrefixed(sender, "chat.no-reply-target");
                        return;
                    }

                    IgnoreManager ignoreManager = plugin.getIgnoreManager();
                    if (ignoreManager.isIgnoring(target, sender)) {
                        MessageUtil.sendPrefixed(sender, "chat.target-ignoring-you");
                        return;
                    }

                    Component msgComponent = Component.text(message);

                    // Отправителю — <viewer> = target
                    Component toSender = plugin.getChatFormatter()
                            .formatReplyToSender(sender, target, msgComponent);
                    sender.sendMessage(toSender);

                    // Получателю — <viewer> = sender
                    Component toReceiver = plugin.getChatFormatter()
                            .formatReplyToReceiver(sender, target, msgComponent);
                    target.sendMessage(toReceiver);

                    // Логирование
                    if (plugin.getChatLogger() != null) {
                        plugin.getChatLogger().log("REPLY", sender.getName(), target.getName(), message);
                    }

                    // Соц-шпион
                    MessageUtil.notifySocialSpy(plugin, "REPLY", sender, target, message);

                    // Обновляем lastReply
                    replyManager.setLastReply(sender, target);
                    replyManager.setLastReply(target, sender);
                })
                .register();
    }
}