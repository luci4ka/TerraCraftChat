package ru.net.terracraft.chat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Одноразовые токены для просмотра инвентаря через [inv].
 * Токен привязан к:
 *   - владельцу инвентаря (target)
 *   - тому, кто может посмотреть (viewer)
 *   - времени жизни (TTL)
 */
public class InvTokenManager {

    private final JavaPlugin plugin;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    public InvTokenManager(JavaPlugin plugin) {
        this.plugin = plugin;

        // Чистим протухшие токены раз в 30 секунд
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::cleanup, 600L, 600L
        );
    }

    /**
     * Создаёт токен для конкретной пары (target, viewer).
     *
     * @param target  чей инвентарь показываем
     * @param viewer  кому разрешено смотреть
     * @param ttlMs   время жизни в миллисекундах
     */
    public String create(Player target, Player viewer, long ttlMs) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        tokens.put(token, new Token(
                target.getUniqueId(),
                viewer.getUniqueId(),
                System.currentTimeMillis() + ttlMs
        ));
        return token;
    }

    /**
     * Проверяет токен и возвращает target, если токен валиден
     * для указанного viewer. Иначе — null.
     */
    public Player resolve(String token, Player viewer) {
        if (token == null) return null;

        Token t = tokens.get(token);
        if (t == null) return null;

        if (System.currentTimeMillis() > t.expiresAt) {
            tokens.remove(token);
            return null;
        }

        if (!t.viewerId.equals(viewer.getUniqueId())) {
            return null;
        }

        Player target = Bukkit.getPlayer(t.targetId);
        if (target == null || !target.isOnline()) {
            tokens.remove(token);
            return null;
        }
        return target;
    }

    /**
     * Удаляет токен после использования (одноразовый).
     */
    public void invalidate(String token) {
        if (token != null) tokens.remove(token);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> now > e.getValue().expiresAt);
    }

    private record Token(UUID targetId, UUID viewerId, long expiresAt) {}
}