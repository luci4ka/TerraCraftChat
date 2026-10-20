package ru.net.terracraft.chat.manager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.net.terracraft.chat.model.Channel;
import ru.net.terracraft.chat.storage.StorageManager;

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

    private final JavaPlugin plugin;
    private final StorageManager storage;

    private volatile Map<String, Channel> channels = Map.of();
    private volatile List<Channel> prefixed = List.of();
    private volatile Channel defaultChannel;

    private final Map<UUID, String> activeByPlayer = new ConcurrentHashMap<>();

    public ChannelManager(JavaPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load(Collection<Channel> list, String defaultId) {
        Map<String, Channel> map = new LinkedHashMap<>();
        for (Channel ch : list) {
            map.put(ch.id(), ch);
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
     * Предзагрузка активного канала игрока при входе.
     * Убирает sync DB read из async-чата.
     */
    public void preload(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeByPlayer.containsKey(uuid)) return;

        String id = storage.getActiveChannel(uuid);
        if (id != null) {
            activeByPlayer.put(uuid, id);
        }
    }

    public Resolved resolve(Player sender, String message) {
        // 1. Префиксы — длинные первыми
        List<Channel> pref = this.prefixed;
        for (Channel ch : pref) {
            String stripped = ch.stripPrefix(message);
            if (stripped != null) {
                return new Resolved(ch, stripped);
            }
        }

        // 2. Активный канал (без sync DB — preload в onJoin)
        String activeId = activeByPlayer.get(sender.getUniqueId());
        Channel active = get(activeId);
        if (active != null) {
            if (canUse(sender, active)) {
                return new Resolved(active, message);
            }
            // Право пропало — сбрасываем (кэш + БД async)
            clearActive(sender);
        }

        // 3. По умолчанию
        return new Resolved(defaultChannel, message);
    }

    public boolean canUse(Player player, Channel channel) {
        if (channel == null) return false;
        String perm = channel.permission();
        if (perm == null || perm.isEmpty()) return true;
        return player.hasPermission(perm);
    }

    public Channel getActive(Player player) {
        String id = activeByPlayer.get(player.getUniqueId());
        Channel ch = get(id);
        return ch != null ? ch : defaultChannel;
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
        for (Channel ch : this.channels.values()) {
            if (canUse(player, ch)) result.add(ch);
        }
        return result;
    }

    public record Resolved(Channel channel, String message) {}
}