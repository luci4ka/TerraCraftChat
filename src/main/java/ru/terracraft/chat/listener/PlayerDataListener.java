package ru.terracraft.chat.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.manager.NickManager;

/**
 * Прогрев данных игрока при входе и очистка кэшей при выходе.
 *
 * <p>Всё, что требует БД, уходит на виртуальные потоки: main-поток
 * не ждёт ни один {@code SELECT}.
 */
public class PlayerDataListener implements Listener {

    private final TerraCraftChatPlugin plugin;
    private final NickManager nickManager;

    public PlayerDataListener(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
        this.nickManager = plugin.getNickManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Ник: кэш -> async загрузка -> displayName в main
        nickManager.load(player);

        plugin.getHeadDisplayManager().hideAllFrom(player);
        plugin.getIgnoreManager().load(player);
        plugin.getChannelManager().preload(player);
        plugin.getReplyManager().load(player);
        plugin.getSkinHeadCache().refresh(player);

        var placeholders = plugin.getChatPlaceholders();
        if (placeholders != null) {
            placeholders.preload(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        nickManager.unload(player);
        plugin.getIgnoreManager().unload(player);
        plugin.getReplyManager().clear(player);
        plugin.getChatModerator().clear(player.getUniqueId());
        plugin.getThreadManager().clearActive(player);
        plugin.getSkinHeadCache().remove(player);
        plugin.getHeadDisplayManager().remove(player);
        plugin.getSocialSpyManager().disable(player);
        plugin.getChannelManager().clearActive(player);
        plugin.getGroupManager().clearActive(player);

        var placeholders = plugin.getChatPlaceholders();
        if (placeholders != null) {
            placeholders.invalidateStats(player.getUniqueId());
        }
    }
}
