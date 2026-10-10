package ru.net.terracraft.chat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class MessageUtil {

    private MessageUtil() {}

    // ============================================================
    //  ОТПРАВКА ПЕРЕВОДОВ
    // ============================================================

    /**
     * Отправляет переведённое сообщение без префикса.
     */
    public static void send(Audience audience, String key, ComponentLike... args) {
        audience.sendMessage(Component.translatable(key, args));
    }

    /**
     * Отправляет переведённое сообщение с префиксом chat.prefix.
     */
    public static void sendPrefixed(Audience audience, String key, ComponentLike... args) {
        Component prefix = Component.translatable("chat.prefix");
        Component message = Component.translatable(key, args);
        audience.sendMessage(prefix.append(message));
    }

    // ============================================================
    //  СОЦ-ШПИОН
    // ============================================================

    /**
     * Отправляет копию ЛС всем, у кого включён соц-шпион.
     * Не отправляет самому отправителю и получателю.
     */
    public static void notifySocialSpy(TerraCraftChatPlugin plugin,
                                       String type,
                                       Player sender,
                                       Player receiver,
                                       String message) {
        // Проверяем, включён ли соц-шпион вообще
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
            if (viewer.equals(sender) || viewer.equals(receiver)) continue;

            viewer.sendMessage(spyMessage);
        }
    }
}