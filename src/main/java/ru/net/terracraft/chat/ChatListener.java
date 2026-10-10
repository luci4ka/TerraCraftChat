package ru.net.terracraft.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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

        Player sender = event.getPlayer();
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!sender.isOnline()) return;

            // ===== 0. РЕЖИМ ТРЕДА =====
            if (plugin.getThreadManager().isActive(sender)) {
                if (plugin.getThreadManager().postReply(sender, plainMessage)) {
                    return;
                }
                plugin.getThreadManager().clearActive(sender);
            }

            // ===== 1. CHAT GAMES =====
            if (plugin.getChatGameManager().checkAnswer(sender, plainMessage)) {
                return;
            }

            // ===== 2. МОДЕРАЦИЯ =====
            ChatModerator.CheckResult result = plugin.getChatModerator().check(sender, plainMessage);

            switch (result.result()) {
                case COOLDOWN -> { MessageUtil.send(sender, "chat.cooldown",
                        Argument.string("seconds", String.valueOf(result.cooldownRemaining()))); return; }
                case DUPLICATE -> { MessageUtil.send(sender, "chat.duplicate"); return; }
                case FLOOD -> { MessageUtil.send(sender, "chat.flood"); return; }
                case SIMILAR -> { MessageUtil.send(sender, "chat.similar"); return; }
                case SWEAR, SWEAR_BLOCK -> { MessageUtil.send(sender, "chat.swear"); return; }
                case ADVERTISEMENT -> { MessageUtil.send(sender, "chat.anti-ad"); return; }
                case CRASH -> { MessageUtil.send(sender, "chat.crash"); return; }
                case OK -> { /* продолжаем */ }
            }

            // ===== 3. КАНАЛ =====
            String processedMessage = result.message();
            String globalPrefix = plugin.getChatConfig().channels().globalPrefix();
            boolean isGlobal = processedMessage.startsWith(globalPrefix);
            String actualMessage = isGlobal
                    ? processedMessage.substring(globalPrefix.length()).trim()
                    : processedMessage;

            if (actualMessage.isEmpty()) return;

            // ===== 4. КЭШ МОДЕРАЦИИ =====
            plugin.getChatModerator().record(sender, result.message());

            // ===== 5. РАССЫЛКА =====
            if (isGlobal) {
                handleGlobal(sender, actualMessage);
            } else {
                handleLocal(sender, actualMessage);
            }
        });
    }

    private void handleGlobal(Player sender, String message) {
        log("GLOBAL", sender, message);

        // Звук упомянутым — один раз для всех зрителей
        formatter.notifyMentions(sender, message);

        ChatMessage registered = plugin.getChatMessageRegistry()
                .register(sender.getName(), message);

        plugin.getDiscordWebhook().queue(sender.getName(), sender.getWorld().getName(), message);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (ignoreManager.isIgnoring(viewer, sender)) continue;

            Component formatted = formatter.formatGlobal(sender, message, viewer, registered.getId());
            viewer.sendMessage(formatted);
            registered.addViewer(viewer.getUniqueId());
        }

        // Локальные ответы бота
        plugin.getLocalAnswerManager().checkAndRespond(message);
    }

    private void handleLocal(Player sender, String message) {
        log("LOCAL", sender, message);

        formatter.notifyMentions(sender, message);

        ChatMessage registered = plugin.getChatMessageRegistry()
                .register(sender.getName(), message);

        int radius = plugin.getChatConfig().channels().localRadius();
        int radiusSq = radius * radius;
        boolean senderAlreadyGot = false;

        for (Player viewer : sender.getWorld().getPlayers()) {
            if (ignoreManager.isIgnoring(viewer, sender)) continue;
            if (viewer.getLocation().distanceSquared(sender.getLocation()) > radiusSq) continue;

            Component formatted = formatter.formatLocal(sender, message, viewer, registered.getId());
            viewer.sendMessage(formatted);
            registered.addViewer(viewer.getUniqueId());

            if (viewer.equals(sender)) senderAlreadyGot = true;
        }

        if (!senderAlreadyGot) {
            Component formatted = formatter.formatLocal(sender, message, sender, registered.getId());
            sender.sendMessage(formatted);
            registered.addViewer(sender.getUniqueId());
        }

        if (plugin.getChatConfig().display().headDisplayEnabled()) {
            plugin.getLogger().info("[DEBUG] Head display enabled, showing for " + sender.getName());
            Component headMessage = formatter.formatLocal(sender, message, sender, null);
            headDisplayManager.showMessage(sender, headMessage);
        } else {
            plugin.getLogger().info("[DEBUG] Head display DISABLED in config");
        }
    }

    // ===== Логирование =====

    private void log(String type, Player sender, String message) {
        if (plugin.getChatLogger() != null) {
            plugin.getChatLogger().log(type, sender.getName(), null, message);
        }
    }
}