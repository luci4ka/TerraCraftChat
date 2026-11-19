package ru.terracraft.chat.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.model.Channel;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.Tasks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PAPI-расширение {@code %terracraftchat_...%}.
 *
 * <p>Плейсхолдеры вызываются из таба/скорборда на каждом тике, поэтому
 * {@link #onRequest} <b>никогда</b> не обращается к БД: значение берётся из
 * кэша, а его обновление идёт на виртуальном потоке. Первое обращение после
 * входа игрока отдаёт нули — к следующему обновлению таба данные уже придут.
 */
public class ChatPlaceholders extends PlaceholderExpansion {

    private static final long STATS_CACHE_TTL_MS = 10_000L;

    private final TerraCraftChatPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final Map<UUID, CachedStats> statsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> statsLoading = new ConcurrentHashMap<>();

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
        String key = params.toLowerCase();

        if (player == null) {
            return switch (key) {
                case "channels" -> String.valueOf(plugin.getChannelManager().all().size());
                case "threads_enabled" -> String.valueOf(plugin.getChatConfig().threads().enabled());
                case "games_count" -> String.valueOf(plugin.getGamesConfig().getGames().size());
                case "storage" -> plugin.getStorageManager().isAvailable() ? "true" : "false";
                default -> null;
            };
        }

        Player online = player.getPlayer();
        String name = player.getName() != null ? player.getName() : "";

        switch (key) {
            case "nick_formatted", "nick_mm":
                if (online != null) {
                    var nickComponent = plugin.getNickManager().getNickComponent(online);
                    if (nickComponent != null) return miniMessage.serialize(nickComponent);
                }
                return "<white>" + name + "</white>";

            case "displayname_formatted", "displayname_mm":
                if (online != null) return miniMessage.serialize(online.displayName());
                return "<white>" + name + "</white>";

            case "nick":
                if (online != null) {
                    var nickComponent = plugin.getNickManager().getNickComponent(online);
                    if (nickComponent != null) {
                        return PlainTextComponentSerializer.plainText().serialize(nickComponent);
                    }
                }
                return name;

            case "displayname":
                if (online != null) {
                    return PlainTextComponentSerializer.plainText().serialize(online.displayName());
                }
                return name;

            case "channel":
                if (online != null) {
                    Channel channel = plugin.getChannelManager().getActive(online);
                    return channel != null ? channel.id() : "unknown";
                }
                return plugin.getChatConfig().channels().defaultChannel();

            case "channel_prefix":
                if (online != null) {
                    Channel channel = plugin.getChannelManager().getActive(online);
                    return channel != null ? channel.prefix() : "";
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
                    return String.valueOf(plugin.getSocialSpyManager().isEnabled(online));
                }
                return "false";

            case "thread_active":
                if (online != null) {
                    return String.valueOf(plugin.getThreadManager().isActive(online));
                }
                return "false";

            case "stats_sent":
            case "stats_blocked":
            case "stats_swear":
            case "stats_ads": {
                if (online == null) return "0";
                StorageManager.PlayerStats stats = statsCached(online.getUniqueId());
                return switch (key) {
                    case "stats_sent" -> String.valueOf(stats.messagesSent());
                    case "stats_blocked" -> String.valueOf(stats.messagesBlocked());
                    case "stats_swear" -> String.valueOf(stats.swearCaught());
                    default -> String.valueOf(stats.adsCaught());
                };
            }

            default:
                return null;
        }
    }

    /**
     * Значение из кэша; при промахе запускает фоновую загрузку и возвращает
     * последнее известное значение (или нули).
     */
    private StorageManager.PlayerStats statsCached(UUID uuid) {
        CachedStats cached = statsCache.get(uuid);
        if (cached != null && cached.valid()) {
            return cached.stats();
        }

        refreshStats(uuid);
        return cached != null ? cached.stats() : new StorageManager.PlayerStats(0, 0, 0, 0);
    }

    /** Асинхронно обновляет статистику (не чаще одного запроса на игрока за раз). */
    public void refreshStats(UUID uuid) {
        if (!plugin.getStorageManager().isAvailable()) return;

        long now = System.currentTimeMillis();
        Long loadingSince = statsLoading.get(uuid);
        if (loadingSince != null && now - loadingSince < STATS_CACHE_TTL_MS) return;
        statsLoading.put(uuid, now);

        Tasks.supply(plugin, "papi-stats", () -> plugin.getStorageManager().getPlayerStats(uuid))
                .thenAccept(stats -> {
                    statsLoading.remove(uuid);
                    if (stats == null) return;
                    statsCache.put(uuid, new CachedStats(stats,
                            System.currentTimeMillis() + STATS_CACHE_TTL_MS));
                });
    }

    /** Прогрев кэша при входе игрока. */
    public void preload(Player player) {
        refreshStats(player.getUniqueId());
    }

    public void invalidateStats(UUID uuid) {
        statsCache.remove(uuid);
        statsLoading.remove(uuid);
    }

    public void invalidateAll() {
        statsCache.clear();
        statsLoading.clear();
    }
}
