package ru.terracraft.chat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.Tasks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Последний собеседник для {@code /reply}.
 *
 * <p>Значение подгружается асинхронно при входе игрока. Если игрок успел
 * написать {@code /r} раньше, чем пришёл ответ из БД, выполняется один
 * синхронный запрос — но это редкий случай, а не каждое сообщение.
 */
public class ReplyManager {

    private final TerraCraftChatPlugin plugin;
    private final StorageManager storage;

    private final Map<UUID, UUID> cache = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public ReplyManager(TerraCraftChatPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /** Асинхронная предзагрузка при входе. */
    public void load(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        if (loaded.contains(viewerId)) return;

        if (!storage.isAvailable()) {
            loaded.add(viewerId);
            return;
        }

        Tasks.supply(plugin, "reply/" + viewer.getName(), () -> storage.getReply(viewerId))
                .thenAccept(target -> {
                    if (target != null) cache.put(viewerId, target);
                    loaded.add(viewerId);
                });
    }

    public void setLastReply(Player viewer, Player target) {
        UUID viewerId = viewer.getUniqueId();
        cache.put(viewerId, target.getUniqueId());
        loaded.add(viewerId);
        storage.setReply(viewerId, target.getUniqueId());
    }

    public Player getLastReply(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = cache.get(viewerId);

        if (targetId == null && !loaded.contains(viewerId)) {
            // Игрок только что зашёл и ещё не успел написать — единственный
            // синхронный запрос за сессию.
            targetId = storage.getReply(viewerId);
            loaded.add(viewerId);
            if (targetId != null) cache.put(viewerId, targetId);
        }

        return targetId != null ? Bukkit.getPlayer(targetId) : null;
    }

    public void clear(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        cache.remove(viewerId);
        loaded.remove(viewerId);
        storage.clearReply(viewerId);
    }
}
