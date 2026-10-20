package ru.net.terracraft.chat.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.manager.SocialSpyManager;

public final class MessageUtil {

    private MessageUtil() {}

    public static void send(Audience audience, String key, ComponentLike... args) {
        audience.sendMessage(Component.translatable(key, args));
    }

    public static void sendPrefixed(Audience audience, String key, ComponentLike... args) {
        Component prefix = Component.translatable("chat.prefix");
        Component message = Component.translatable(key, args);
        audience.sendMessage(prefix.append(message));
    }

    /**
     * Отправляет копию ЛС всем, у кого включён соц-шпион.
     * Проверяет право terracraftchat.socialspy — на случай, если флаг включён,
     * а право отозвали.
     */
    public static void notifySocialSpy(TerraCraftChatPlugin plugin,
                                       String type,
                                       Player sender,
                                       Player receiver,
                                       String message) {
        if (!plugin.getChatConfig().socialSpy().enabled()) {
            return;
        }

        SocialSpyManager spy = plugin.getSocialSpyManager();
        if (spy.getAll().isEmpty()) {
            return;
        }

        Component spyMessage = plugin.getChatFormatter()
                .formatSocialSpy(type, sender, receiver, message);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!spy.isEnabled(viewer)) continue;
            if (!viewer.hasPermission("terracraftchat.socialspy")) continue;
            if (viewer.equals(sender) || viewer.equals(receiver)) continue;

            viewer.sendMessage(spyMessage);
        }
    }
}