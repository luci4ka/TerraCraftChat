package ru.net.terracraft.chat.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.format.ChatFormatter;
import ru.net.terracraft.chat.format.HeadDisplayManager;
import ru.net.terracraft.chat.manager.ChannelManager;
import ru.net.terracraft.chat.manager.IgnoreManager;
import ru.net.terracraft.chat.model.Channel;
import ru.net.terracraft.chat.model.ChatMessage;
import ru.net.terracraft.chat.moderation.ChatModerator;
import ru.net.terracraft.chat.moderation.Rule;
import ru.net.terracraft.chat.util.MessageUtil;

import java.util.*;

public class ChatListener implements Listener {

    private final TerraCraftChatPlugin plugin;
    private final IgnoreManager ignoreManager;
    private final HeadDisplayManager headDisplayManager;
    private final ChatFormatter formatter;

    public ChatListener(TerraCraftChatPlugin plugin,
                        IgnoreManager ignoreManager,
                        HeadDisplayManager headDisplayManager,
                        ChatFormatter formatter) {
        this.plugin = plugin;
        this.ignoreManager = ignoreManager;
        this.headDisplayManager = headDisplayManager;
        this.formatter = formatter;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        final Player sender = event.getPlayer();
        final UUID senderId = sender.getUniqueId();
        final String plainMessage = PlainTextComponentSerializer.plainText()
                .serialize(event.message());

        // ===== ФАЗА 1: async =====
        ChannelManager cm = plugin.getChannelManager();
        ChannelManager.Resolved resolved = cm.resolve(sender, plainMessage);
        Channel channel = resolved.channel();

        if (channel == null || !cm.canUse(sender, channel)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (sender.isOnline()) MessageUtil.sendPrefixed(sender, "chat.channel-no-access");
            });
            return;
        }

        ChatModerator.Bypass bypass = new ChatModerator.Bypass(
                sender.hasPermission("terracraftchat.bypass.ads"),
                sender.hasPermission("terracraftchat.bypass.swear"),
                sender.hasPermission("terracraftchat.bypass.levenshtein"),
                sender.hasPermission("terracraftchat.bypass.flood"),
                sender.hasPermission("terracraftchat.bypass.crash"),
                sender.hasPermission("terracraftchat.bypass.cooldown"),
                sender.hasPermission("terracraftchat.bypass.duplicate")
        );
        ChatModerator.CheckResult result = plugin.getChatModerator()
                .check(senderId, resolved.message(), bypass);

        // ВАЖНО: записываем ИСХОДНОЕ сообщение (без цензуры),
        // чтобы DUPLICATE/LEVENSHTEIN сравнивали оригиналы.
        if (result.result() == ChatModerator.Result.OK
                || result.result() == ChatModerator.Result.SWEAR) {
            plugin.getChatModerator().record(senderId, resolved.message());
        }

        // Блокирующие случаи — сообщение игроку в main
        if (result.result() != ChatModerator.Result.OK
                && result.result() != ChatModerator.Result.SWEAR) {
            var storage = plugin.getStorageManager();
            if (storage != null) {
                switch (result.result()) {
                    case SWEAR_BLOCK -> storage.incrementStats(senderId, "messages_blocked", "swear_caught");
                    case ADVERTISEMENT -> storage.incrementStats(senderId, "messages_blocked", "ads_caught");
                    default -> storage.incrementStats(senderId, "messages_blocked");
                }
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!sender.isOnline()) return;
                switch (result.result()) {
                    case COOLDOWN -> MessageUtil.send(sender, "chat.cooldown",
                            Argument.string("seconds", String.valueOf(result.cooldownRemaining())));
                    case DUPLICATE -> MessageUtil.send(sender, "chat.duplicate");
                    case FLOOD -> MessageUtil.send(sender, "chat.flood");
                    case SIMILAR -> MessageUtil.send(sender, "chat.similar");
                    case SWEAR_BLOCK -> MessageUtil.send(sender, "chat.swear");
                    case ADVERTISEMENT -> MessageUtil.send(sender, "chat.anti-ad");
                    case CRASH -> MessageUtil.send(sender, "chat.crash");
                    default -> { }
                }
            });
            return;
        }

        final String actualMessage = result.message();
        if (actualMessage.isEmpty()) return;

        final Channel finalChannel = channel;

        // ===== ФАЗА 2: main =====
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!sender.isOnline()) return;

            // === RULE ENGINE (main, т.к. dispatchCommand) ===
            Rule rule = plugin.getRuleManager().match(actualMessage);
            if (rule != null) {
                plugin.getRuleManager().execute(rule, sender);
                if (rule.cancelMessage()) {
                    return; // сообщение не рассылаем
                }
            }

            // Chat game
            if (plugin.getChatGameManager().checkAnswer(sender, actualMessage)) return;

            // Тред
            if (plugin.getThreadManager().isActive(sender)) {
                plugin.getThreadManager().postReply(sender, actualMessage);
                return;
            }

            // Зрители
            List<Player> viewers = collectViewers(sender, finalChannel);
            if (viewers.isEmpty()) return;

            // ChatMessage с channelId (для перерисовки)
            ChatMessage registered = new ChatMessage(
                    sender.getUniqueId(), sender.getName(), actualMessage, finalChannel.id()
            );
            for (Player viewer : viewers) {
                registered.addViewer(viewer.getUniqueId());
            }
            plugin.getChatMessageRegistry().register(registered);

            // Сохраняем в БД (async) — для истории и тредов после перезахода
            var storage = plugin.getStorageManager();
            if (storage != null) {
                storage.saveMessage(
                        registered.getId(),
                        sender.getUniqueId(),
                        sender.getName(),
                        finalChannel.id(),
                        actualMessage,
                        registered.getTimestamp()
                );
                storage.incrementStat(senderId, "messages_sent");

                // Сохраняем viewers — для восстановления истории при старте
                Set<UUID> viewerIds = new HashSet<>();
                for (Player v : viewers) viewerIds.add(v.getUniqueId());
                storage.saveMessageViewers(registered.getId(), viewerIds);

            }

            String format = plugin.getChatConfig().formats().byKey(finalChannel.formatKey());
            for (Player viewer : viewers) {
                Component formatted = formatter.formatChannel(
                        sender, actualMessage, viewer, format, registered.getId());
                viewer.sendMessage(formatted);
            }

            handleSideEffects(sender, finalChannel, format, actualMessage, viewers);
        });
    }

    private void handleSideEffects(Player sender, Channel channel, String format,
                                   String message, List<Player> viewers) {
        String type = channel.isGlobal() ? "GLOBAL" : "LOCAL";
        log(type, sender, message);

        if (channel.isPublic()) {
            formatter.notifyMentions(sender, message, viewers);
        }

        String consoleLine = "[" + channel.id().toUpperCase(Locale.ROOT) + "] "
                + sender.getName() + ": " + message;
        plugin.getServer().getConsoleSender().sendMessage(consoleLine);

        if (channel.isPublic()) {
            plugin.getDiscordWebhook().queue(
                    sender.getName(), sender.getWorld().getName(), message);
            plugin.getLocalAnswerManager().checkAndRespond(message);
        }

        if (!channel.isGlobal()
                && plugin.getChatConfig().display().headDisplayEnabled()) {
            Component head = formatter.formatChannel(
                    sender, message, sender, format, null);
            headDisplayManager.showMessage(sender, head);
        }
    }

    private List<Player> collectViewers(Player sender, Channel channel) {
        ChannelManager cm = plugin.getChannelManager();
        List<Player> result = new ArrayList<>();

        if (channel.isGlobal()) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!cm.canUse(viewer, channel)) continue;
                if (ignoreManager.isIgnoring(viewer, sender)) continue;
                if (!viewer.canSee(sender)) continue;
                result.add(viewer);
            }
            return result;
        }

        int rangeSq = channel.range() * channel.range();
        boolean senderIncluded = false;

        for (Player viewer : sender.getWorld().getPlayers()) {
            if (!cm.canUse(viewer, channel)) continue;
            if (ignoreManager.isIgnoring(viewer, sender)) continue;
            if (!viewer.canSee(sender)) continue;
            if (viewer.getLocation().distanceSquared(sender.getLocation()) > rangeSq) continue;

            result.add(viewer);
            if (viewer.equals(sender)) senderIncluded = true;
        }

        if (!senderIncluded) {
            result.add(sender);
        }

        return result;
    }

    private void log(String type, Player sender, String message) {
        if (plugin.getChatLogger() != null) {
            plugin.getChatLogger().log(type, sender.getName(), null, message);
        }
    }
}