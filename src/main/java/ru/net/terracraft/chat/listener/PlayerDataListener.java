package ru.net.terracraft.chat.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.manager.NickManager;

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

        // --- Nick (sync, быстро — из кэша/БД 1 запрос) ---
        nickManager.load(player);
        Component nick = nickManager.getNickComponent(player);
        if (nick != null) {
            player.displayName(nick);
        }

        // --- Player data (async внутри) ---
        plugin.getHeadDisplayManager().hideAllFrom(player);
        plugin.getIgnoreManager().load(player);         // async
        plugin.getChannelManager().preload(player);     // async
        plugin.getSkinHeadCache().refresh(player);
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
    }
}