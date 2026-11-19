package ru.terracraft.chat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.Tasks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Групповые чаты.
 *
 * <p>Все методы, обращающиеся к БД, синхронные, но предназначены для вызова
 * внутри задач {@link Tasks#supply} — с main-потока они больше не дёргаются.
 */
public class GroupManager {

    /** Кэш участников живёт 30 секунд, чтобы не читать БД на каждое сообщение. */
    private static final long MEMBERS_CACHE_TTL_MS = 30_000L;

    private final TerraCraftChatPlugin plugin;
    private final StorageManager storage;

    private final Map<UUID, UUID> activeGroupByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, CachedMembers> membersCache = new ConcurrentHashMap<>();

    private record CachedMembers(List<UUID> members, long expiresAt) {
        boolean valid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    public GroupManager(TerraCraftChatPlugin plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    // ============================================================
    //  АКТИВНАЯ ГРУППА
    // ============================================================

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

    // ============================================================
    //  КЭШ
    // ============================================================

    /** Сбрасывает кэш участников (вызывать при add/remove/delete). */
    public void invalidateCache(UUID groupId) {
        if (groupId != null) membersCache.remove(groupId);
    }

    public void invalidateAllCache() {
        membersCache.clear();
    }

    // ============================================================
    //  БД (вызывать из Tasks.supply)
    // ============================================================

    public StorageManager.GroupRow findByName(String name) {
        return storage.getGroupByName(name);
    }

    public UUID create(String name, Player owner) {
        UUID groupId = storage.createGroup(name, owner.getUniqueId());
        if (groupId == null) return null;
        storage.addGroupMember(groupId, owner.getUniqueId());
        invalidateCache(groupId);
        return groupId;
    }

    public boolean addMember(UUID groupId, UUID memberId) {
        boolean ok = storage.addGroupMember(groupId, memberId);
        invalidateCache(groupId);
        return ok;
    }

    public boolean removeMember(UUID groupId, UUID memberId) {
        boolean ok = storage.removeGroupMember(groupId, memberId);
        invalidateCache(groupId);
        return ok;
    }

    public void delete(UUID groupId) {
        storage.deleteGroup(groupId);
        invalidateCache(groupId);
    }

    /** Участники группы с кэшем 30 секунд. */
    public List<UUID> members(UUID groupId) {
        CachedMembers cached = membersCache.get(groupId);
        if (cached != null && cached.valid()) {
            return cached.members();
        }

        List<UUID> fresh = List.copyOf(storage.getGroupMembers(groupId));
        membersCache.put(groupId, new CachedMembers(
                fresh,
                System.currentTimeMillis() + MEMBERS_CACHE_TTL_MS
        ));
        return fresh;
    }

    /**
     * Отправляет сообщение в группу. Вызывать из async-задачи;
     * рассылка сама вернётся в main-поток.
     *
     * @return {@code false}, если отправитель не участник группы
     */
    public boolean sendToGroup(Player sender, StorageManager.GroupRow group, String message) {
        List<UUID> members = members(group.id());

        if (!members.contains(sender.getUniqueId())) {
            return false;
        }

        UUID messageId = UUID.randomUUID();
        storage.saveGroupMessage(messageId, group.id(), sender.getUniqueId(),
                sender.getName(), message, System.currentTimeMillis());

        Tasks.sync(plugin, () -> {
            for (UUID memberId : members) {
                Player viewer = Bukkit.getPlayer(memberId);
                if (viewer == null || !viewer.isOnline()) continue;
                if (plugin.getIgnoreManager().isIgnoring(viewer, sender)) continue;

                viewer.sendMessage(plugin.getChatFormatter().formatGroupMessage(
                        sender, group.name(), message, viewer));
            }
        });

        return true;
    }

    /** Возвращает набор всех онлайн-групп (для дебага/плацехолдеров). */
    public Set<UUID> getActiveGroupsSnapshot() {
        return Set.copyOf(activeGroupByPlayer.values());
    }
}
