package ru.terracraft.chat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.model.Channel;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.Tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {

    private final TerraCraftChatPlugin plugin;
    private final StorageManager storage;

    private volatile Map<String, Channel> channels = Map.of();
    private volatile List<Channel> prefixed = List.of();
    private volatile Channel defaultChannel;

    private final Map<UUID, String> activeByPlayer = new ConcurrentHashMap<>();

    public ChannelManager(TerraCraftChatPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load(Collection<Channel> list, String defaultId) {
        Map<String, Channel> map = new LinkedHashMap<>();
        for (Channel channel : list) {
            map.put(channel.id(), channel);
        }

        List<Channel> pref = map.values().stream()
                .filter(Channel::hasPrefix)
                .sorted(Comparator.comparingInt(
                        (Channel c) -> c.prefix().length()).reversed())
                .toList();

        Channel def = null;
        if (defaultId != null && !defaultId.isEmpty()) {
            def = map.get(defaultId.toLowerCase(Locale.ROOT));
        }
        if (def == null) {
            def = map.values().stream().findFirst().orElse(null);
        }

        this.channels = Collections.unmodifiableMap(map);
        this.prefixed = pref;
        this.defaultChannel = def;

        if (def == null) {
            plugin.getLogger().warning("ChannelManager: defaultChannel = null (пустой список каналов)");
        }
    }

    public Channel getDefault() {
        return defaultChannel;
    }

    public Channel get(String id) {
        if (id == null) return null;
        return this.channels.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Channel> all() {
        return this.channels.values();
    }

    /**
     * Асинхронно подгружает активный канал при входе.
     * До загрузки resolve() вернёт defaultChannel.
     */
    public void preload(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeByPlayer.containsKey(uuid)) return;
        if (!storage.isAvailable()) return;

        Tasks.supply(plugin, "channel/" + player.getName(), () -> storage.getActiveChannel(uuid))
                .thenAccept(id -> {
                    if (id == null) return;
                    if (!player.isOnline()) return;

                    // Если активный канал уже установлен (пока грузились) — не перезаписываем
                    activeByPlayer.putIfAbsent(uuid, id);
                });
    }

    public Resolved resolve(Player sender, String message) {
        List<Channel> pref = this.prefixed;
        for (Channel channel : pref) {
            String stripped = channel.stripPrefix(message);
            if (stripped != null) {
                return new Resolved(channel, stripped);
            }
        }

        String activeId = activeByPlayer.get(sender.getUniqueId());
        Channel active = get(activeId);
        if (active != null) {
            if (canUse(sender, active)) {
                return new Resolved(active, message);
            }
            clearActive(sender);
        }

        return new Resolved(defaultChannel, message);
    }

    public boolean canUse(Player player, Channel channel) {
        if (channel == null) return false;
        String permission = channel.permission();
        if (permission == null || permission.isEmpty()) return true;
        return player.hasPermission(permission);
    }

    public Channel getActive(Player player) {
        String id = activeByPlayer.get(player.getUniqueId());
        Channel channel = get(id);
        return channel != null ? channel : defaultChannel;
    }

    public void setActive(Player player, Channel channel) {
        if (channel == null) {
            clearActive(player);
        } else {
            activeByPlayer.put(player.getUniqueId(), channel.id());
            storage.setActiveChannel(player.getUniqueId(), channel.id());
        }
    }

    public void clearActive(Player player) {
        activeByPlayer.remove(player.getUniqueId());
        storage.clearActiveChannel(player.getUniqueId());
    }

    public List<Channel> getAvailable(Player player) {
        List<Channel> result = new ArrayList<>();
        for (Channel channel : this.channels.values()) {
            if (canUse(player, channel)) result.add(channel);
        }
        return result;
    }

    /** Онлайн-игроки, которые видят канал (используется в PAPI-плейсхолдерах). */
    public int onlineInChannel(Channel channel) {
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (canUse(online, channel)) count++;
        }
        return count;
    }

    public record Resolved(Channel channel, String message) {}
}
