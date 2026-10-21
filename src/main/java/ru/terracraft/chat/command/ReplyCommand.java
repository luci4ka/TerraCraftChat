package ru.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.manager.IgnoreManager;
import ru.terracraft.chat.manager.ReplyManager;
import ru.terracraft.chat.util.MessageUtil;

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

                    // 1. Target игнорирует sender — target не хочет получать
                    if (ignoreManager.isIgnoring(target, sender)) {
                        MessageUtil.sendPrefixed(sender, "chat.target-ignoring-you");
                        return;
                    }

                    // 2. Sender игнорирует target — sender всё равно пишет
                    if (ignoreManager.isIgnoring(sender, target)) {
                        MessageUtil.sendPrefixed(sender, "chat.you-ignoring-target",
                                Argument.string("player", target.getName()));
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