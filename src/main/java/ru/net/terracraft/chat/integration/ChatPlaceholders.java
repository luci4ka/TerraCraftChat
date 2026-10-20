package ru.net.terracraft.chat.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.format.NickFormat;
import ru.net.terracraft.chat.model.Channel;

public class ChatPlaceholders extends PlaceholderExpansion {

    private final TerraCraftChatPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatPlaceholders(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "terracraftchat";
    }

    @Override
    public @NotNull String getAuthor() {
        return "luci4ka";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            // Серверные плейсхолдеры
            return switch (params.toLowerCase()) {
                case "channels" -> String.valueOf(plugin.getChannelManager().all().size());
                case "threads_enabled" -> plugin.getChatConfig().threads().enabled() ? "true" : "false";
                case "games_count" -> String.valueOf(plugin.getGamesConfig().getGames().size());
                default -> null;
            };
        }

        Player online = player.getPlayer();
        String name = player.getName() != null ? player.getName() : "";

        switch (params.toLowerCase()) {
            // ===== Отформатированные (MiniMessage-теги, для таба) =====
            case "nick_formatted", "nick_mm":
                if (online != null) {
                    var nickComp = plugin.getNickManager().getNickComponent(online);
                    if (nickComp != null) {
                        return miniMessage.serialize(nickComp);
                    }
                }
                return "<white>" + name + "</white>";

            case "displayname_formatted", "displayname_mm":
                if (online != null) {
                    return miniMessage.serialize(online.displayName());
                }
                return "<white>" + name + "</white>";

            // ===== Plain text (без тегов) =====
            case "nick":
                if (online != null) {
                    var nickComp = plugin.getNickManager().getNickComponent(online);
                    if (nickComp != null) {
                        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                .plainText().serialize(nickComp);
                    }
                }
                return name;

            case "displayname":
                if (online != null) {
                    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(online.displayName());
                }
                return name;

            // ===== Канал =====
            case "channel":
                if (online != null) {
                    Channel ch = plugin.getChannelManager().getActive(online);
                    return ch != null ? ch.id() : "unknown";
                }
                return plugin.getChatConfig().channels().defaultChannel();

            case "channel_prefix":
                if (online != null) {
                    Channel ch = plugin.getChannelManager().getActive(online);
                    return ch != null ? ch.prefix() : "";
                }
                return "";

            // ===== Игнор =====
            case "ignoring_count":
                if (online != null) {
                    return String.valueOf(
                            plugin.getIgnoreManager().getIgnoredByUuid(online.getUniqueId()).size()
                    );
                }
                return "0";

            // ===== Соц-шпион =====
            case "socialspy":
                if (online != null) {
                    return plugin.getSocialSpyManager().isEnabled(online) ? "true" : "false";
                }
                return "false";

            // ===== Тред =====
            case "thread_active":
                if (online != null) {
                    return plugin.getThreadManager().isActive(online) ? "true" : "false";
                }
                return "false";

            default:
                return null;
        }
    }
}