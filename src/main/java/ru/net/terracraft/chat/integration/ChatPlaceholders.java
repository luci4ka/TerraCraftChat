package ru.net.terracraft.chat.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.model.Channel;
import ru.net.terracraft.chat.storage.StorageManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatPlaceholders extends PlaceholderExpansion {

    private static final long STATS_CACHE_TTL_MS = 10_000L;

    private final TerraCraftChatPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Кэш статистики: uuid → (stats, expiresAt)
    private final Map<UUID, CachedStats> statsCache = new ConcurrentHashMap<>();

    private record CachedStats(StorageManager.PlayerStats stats, long expiresAt) {
        boolean valid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    public ChatPlaceholders(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "terracraftchat"; }
    @Override public @NotNull String getAuthor() { return "luci4ka"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
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
            case "nick_formatted", "nick_mm":
                if (online != null) {
                    var nickComp = plugin.getNickManager().getNickComponent(online);
                    if (nickComp != null) return miniMessage.serialize(nickComp);
                }
                return "<white>" + name + "</white>";

            case "displayname_formatted", "displayname_mm":
                if (online != null) return miniMessage.serialize(online.displayName());
                return "<white>" + name + "</white>";

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

            case "ignoring_count":
                if (online != null) {
                    return String.valueOf(
                            plugin.getIgnoreManager().getIgnoredByUuid(online.getUniqueId()).size()
                    );
                }
                return "0";

            case "socialspy":
                if (online != null) {
                    return plugin.getSocialSpyManager().isEnabled(online) ? "true" : "false";
                }
                return "false";

            case "thread_active":
                if (online != null) {
                    return plugin.getThreadManager().isActive(online) ? "true" : "false";
                }
                return "false";

            // ===== Статистика (с кэшем) =====
            case "stats_sent":
            case "stats_blocked":
            case "stats_swear":
            case "stats_ads": {
                if (online == null) return "0";
                var stats = getStatsCached(online.getUniqueId());
                return switch (params.toLowerCase()) {
                    case "stats_sent" -> String.valueOf(stats.messagesSent());
                    case "stats_blocked" -> String.valueOf(stats.messagesBlocked());
                    case "stats_swear" -> String.valueOf(stats.swearCaught());
                    case "stats_ads" -> String.valueOf(stats.adsCaught());
                    default -> "0";
                };
            }

            default:
                return null;
        }
    }

    /**
     * Кэш статистики на 10 сек — чтобы не дёргать БД в табе / скорборде.
     */
    private StorageManager.PlayerStats getStatsCached(UUID uuid) {
        CachedStats cached = statsCache.get(uuid);
        if (cached != null && cached.valid()) {
            return cached.stats();
        }
        StorageManager.PlayerStats fresh = plugin.getStorageManager().getPlayerStats(uuid);
        statsCache.put(uuid, new CachedStats(fresh, System.currentTimeMillis() + STATS_CACHE_TTL_MS));
        return fresh;
    }

    /** Сброс кэша для игрока — вызывать после incrementStat, если хочешь мгновенное обновление. */
    public void invalidateStats(UUID uuid) {
        statsCache.remove(uuid);
    }

    /** Полный сброс (при reload конфига). */
    public void invalidateAll() {
        statsCache.clear();
    }
}