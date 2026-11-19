package ru.terracraft.chat.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.format.ChatFormatter;
import ru.terracraft.chat.format.HeadDisplayManager;
import ru.terracraft.chat.manager.ChannelManager;
import ru.terracraft.chat.manager.IgnoreManager;
import ru.terracraft.chat.model.Channel;
import ru.terracraft.chat.model.ChatMessage;
import ru.terracraft.chat.moderation.ChatModerator;
import ru.terracraft.chat.moderation.Rule;
import ru.terracraft.chat.util.MessageUtil;
import ru.terracraft.chat.util.Tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
        // Плагин сам рендерит и рассылает сообщение (нужно для кнопок удаления,
        // тредов и перерисовки чата).
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
            Tasks.sync(plugin, () -> {
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

        ChatModerator moderator = plugin.getChatModerator();
        ChatModerator.Outcome outcome = moderator.check(senderId, resolved.message(), bypass);

        // ВАЖНО: записываем ИСХОДНОЕ сообщение (без цензуры),
        // чтобы DUPLICATE/LEVENSHTEIN сравнивали оригиналы.
        final String actualMessage = switch (outcome) {
            case ChatModerator.Outcome.Allowed allowed -> {
                moderator.record(senderId, resolved.message());
                yield allowed.message();
            }
            case ChatModerator.Outcome.Censored censored -> {
                moderator.record(senderId, resolved.message());
                yield censored.message();
            }
            case ChatModerator.Outcome.Blocked blocked -> {
                reportBlocked(sender, blocked);
                yield null;
            }
        };

        if (actualMessage == null || actualMessage.isEmpty()) return;

        final Channel finalChannel = channel;

        // ===== ФАЗА 2: main =====
        Tasks.sync(plugin, () -> handleMessage(sender, senderId, actualMessage, finalChannel));
    }

    /**
     * Сообщение игроку о блокировке + статистика.
     * Вызывается из async-контекста события — отправка уходит в main.
     */
    private void reportBlocked(Player sender, ChatModerator.Outcome.Blocked blocked) {
        var storage = plugin.getStorageManager();
        if (storage != null) {
            switch (blocked.reason()) {
                case SWEAR_BLOCK -> storage.incrementStats(sender.getUniqueId(), "messages_blocked", "swear_caught");
                case ADVERTISEMENT -> storage.incrementStats(sender.getUniqueId(), "messages_blocked", "ads_caught");
                default -> storage.incrementStats(sender.getUniqueId(), "messages_blocked");
            }
        }

        Tasks.sync(plugin, () -> {
            if (!sender.isOnline()) return;
            switch (blocked.reason()) {
                case COOLDOWN -> MessageUtil.send(sender, "chat.cooldown",
                        Argument.string("seconds", String.valueOf(blocked.cooldownRemaining())));
                case DUPLICATE -> MessageUtil.send(sender, "chat.duplicate");
                case FLOOD -> MessageUtil.send(sender, "chat.flood");
                case SIMILAR -> MessageUtil.send(sender, "chat.similar");
                case SWEAR_BLOCK -> MessageUtil.send(sender, "chat.swear");
                case ADVERTISEMENT -> MessageUtil.send(sender, "chat.anti-ad");
                case CRASH -> MessageUtil.send(sender, "chat.crash");
                default -> { }
            }
        });
    }

    private void handleMessage(Player sender, UUID senderId, String actualMessage, Channel channel) {
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
        List<Player> viewers = collectViewers(sender, channel);
        if (viewers.isEmpty()) return;

        // ChatMessage с channelId (для перерисовки)
        ChatMessage registered = new ChatMessage(
                sender.getUniqueId(), sender.getName(), actualMessage, channel.id()
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
                    channel.id(),
                    actualMessage,
                    registered.getTimestamp()
            );
            storage.incrementStat(senderId, "messages_sent");

            // Сохраняем viewers — для восстановления истории при старте
            Set<UUID> viewerIds = new HashSet<>();
            for (Player v : viewers) viewerIds.add(v.getUniqueId());
            storage.saveMessageViewers(registered.getId(), viewerIds);
        }

        // Конверт (PAPI, &-коды, голова) считается ОДИН раз на сообщение,
        // а не для каждого зрителя.
        ChatFormatter.Envelope envelope = formatter.envelope(sender, channel.formatKey());
        for (Player viewer : viewers) {
            viewer.sendMessage(formatter.formatChannel(
                    sender, actualMessage, viewer, envelope, registered.getId()));
        }

        handleSideEffects(sender, channel, envelope, actualMessage, viewers);
    }

    private void handleSideEffects(Player sender, Channel channel,
                                   ChatFormatter.Envelope envelope,
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
                    sender, message, sender, envelope, null);
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
