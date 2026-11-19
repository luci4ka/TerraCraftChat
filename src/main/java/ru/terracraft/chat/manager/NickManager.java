package ru.terracraft.chat.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.format.NickFormat;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.Tasks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ники игроков.
 *
 * <p>Загрузка из БД асинхронная: раньше {@code SELECT} выполнялся прямо в
 * {@code PlayerJoinEvent}, то есть в main-потоке — на большой таблице это
 * фриз на входе игрока.
 */
public class NickManager {

    private final TerraCraftChatPlugin plugin;
    private final StorageManager storage;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public NickManager(TerraCraftChatPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /** Асинхронно подгружает ник и обновляет displayName в main-потоке. */
    public void load(Player player) {
        UUID uuid = player.getUniqueId();

        String cached = cache.get(uuid);
        if (cached != null) {
            updateDisplayName(player);
            return;
        }

        if (!storage.isAvailable()) return;

        Tasks.supply(plugin, "nick/" + player.getName(), () -> storage.getNick(uuid))
                .thenAccept(nick -> {
                    if (nick == null || nick.isEmpty()) return;
                    cache.put(uuid, nick);
                    Tasks.sync(plugin, () -> {
                        if (player.isOnline()) updateDisplayName(player);
                    });
                });
    }

    public void setNick(Player player, String nick) {
        cache.put(player.getUniqueId(), nick);
        storage.setNick(player.getUniqueId(), nick);
        updateDisplayName(player);
    }

    public void resetNick(Player player) {
        cache.remove(player.getUniqueId());
        storage.removeNick(player.getUniqueId());
        player.displayName(Component.text(player.getName()));
    }

    public String getNick(Player player) {
        return cache.get(player.getUniqueId());
    }

    public Component getNickComponent(Player player) {
        String raw = cache.get(player.getUniqueId());
        if (raw == null || raw.isEmpty()) return null;
        return NickFormat.SAFE.deserialize(raw);
    }

    private void updateDisplayName(Player player) {
        Component nick = getNickComponent(player);
        player.displayName(nick != null ? nick : Component.text(player.getName()));
    }

    public void unload(Player player) {
        cache.remove(player.getUniqueId());
    }
}
