package ru.terracraft.chat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.storage.StorageManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {

    private static final long MEMBERS_CACHE_TTL_MS = 30_000L; // 30 сек

    private final TerraCraftChatPlugin plugin;
    private final StorageManager storage;

    private final Map<UUID, UUID> activeGroupByPlayer = new ConcurrentHashMap<>();

    // Кэш участников: groupId → (memberIds, expiresAt)
    private final Map<UUID, CachedMembers> membersCache = new ConcurrentHashMap<>();

    private record CachedMembers(List<UUID> members, long expiresAt) {
        boolean isValid() { return System.currentTimeMillis() < expiresAt; }
    }

    public GroupManager(TerraCraftChatPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public StorageManager.GroupRow getByName(String name) {
        return storage.getGroupByName(name);
    }

    public void setActive(Player player, UUID groupId) {
        if (groupId == null) {
            activeGroupByPlayer.remove(player.getUniqueId());
        } else {
            activeGroupByPlayer.put(player.getUniqueId(), groupId);
        }
    }

    public UUID getActive(Player player) {
        return activeGroupByPlayer.get(player.getUniqueId());
    }

    public void clearActive(Player player) {
        activeGroupByPlayer.remove(player.getUniqueId());
    }

    /** Сбрасывает кэш участников (вызывать при add/remove/delete). */
    public void invalidateCache(UUID groupId) {
        if (groupId != null) membersCache.remove(groupId);
    }

    public void invalidateAllCache() {
        membersCache.clear();
    }

    /**
     * Возвращает участников группы. Кэш 30 сек — чтобы не дёргать БД
     * на каждое сообщение в группе.
     */
    private List<UUID> getMembersCached(UUID groupId) {
        CachedMembers cached = membersCache.get(groupId);
        if (cached != null && cached.isValid()) {
            return cached.members();
        }
        List<UUID> fresh = storage.getGroupMembers(groupId);
        membersCache.put(groupId, new CachedMembers(
                List.copyOf(fresh),
                System.currentTimeMillis() + MEMBERS_CACHE_TTL_MS
        ));
        return fresh;
    }

    public boolean sendToGroup(Player sender, StorageManager.GroupRow group, String message) {
        List<UUID> members = getMembersCached(group.id());

        if (!members.contains(sender.getUniqueId())) {
            return false;
        }

        UUID msgId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        storage.saveGroupMessage(msgId, group.id(), sender.getUniqueId(),
                sender.getName(), message, now);

        for (UUID memberId : members) {
            Player viewer = Bukkit.getPlayer(memberId);
            if (viewer == null || !viewer.isOnline()) continue;
            if (plugin.getIgnoreManager().isIgnoring(viewer, sender)) continue;

            var formatted = plugin.getChatFormatter().formatGroupMessage(
                    sender, group.name(), message, viewer);
            viewer.sendMessage(formatted);
        }
        return true;
    }

    /** Возвращает набор всех онлайн-групп (для дебага/плацехолдеров). */
    public Set<UUID> getActiveGroupsSnapshot() {
        return Set.copyOf(activeGroupByPlayer.values());
    }
}