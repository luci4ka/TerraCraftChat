package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.manager.IgnoreManager;
import ru.net.terracraft.chat.manager.ReplyManager;
import ru.net.terracraft.chat.util.MessageUtil;

public class MsgCommand {

    private final TerraCraftChatPlugin plugin;

    public MsgCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("msg")
                .withAliases("tell", "w", "whisper")
                .withPermission("terracraftchat.msg")
                .withArguments(new EntitySelectorArgument.OnePlayer("target"))
                .withArguments(new GreedyStringArgument("message"))
                .executesPlayer((sender, args) -> {
                    Player target = (Player) args.get("target");
                    String message = (String) args.get("message");

                    if (sender.getUniqueId().equals(target.getUniqueId())) {
                        MessageUtil.sendPrefixed(sender, "chat.cannot-message-self");
                        return;
                    }

                    IgnoreManager ignoreManager = plugin.getIgnoreManager();

                    // 1. Target игнорирует sender — target не хочет получать
                    if (ignoreManager.isIgnoring(target, sender)) {
                        MessageUtil.sendPrefixed(sender, "chat.target-ignoring-you");
                        return;
                    }

                    // 2. Sender игнорирует target — sender всё равно пишет
                    //    (обычно разрешено, но предупреждаем — опционально)
                    if (ignoreManager.isIgnoring(sender, target)) {
                        MessageUtil.sendPrefixed(sender, "chat.you-ignoring-target",
                                Argument.string("player", target.getName()));
                        return;
                    }

                    Component msgComponent = Component.text(message);

                    Component toSender = plugin.getChatFormatter()
                            .formatPrivateToSender(sender, target, msgComponent);
                    sender.sendMessage(toSender);

                    Component toReceiver = plugin.getChatFormatter()
                            .formatPrivateToReceiver(sender, target, msgComponent);
                    target.sendMessage(toReceiver);

                    if (plugin.getChatLogger() != null) {
                        plugin.getChatLogger().log("MSG", sender.getName(), target.getName(), message);
                    }

                    MessageUtil.notifySocialSpy(plugin, "MSG", sender, target, message);

                    ReplyManager replyManager = plugin.getReplyManager();
                    replyManager.setLastReply(sender, target);
                    replyManager.setLastReply(target, sender);
                })
                .register();
    }
}