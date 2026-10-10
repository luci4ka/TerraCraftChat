package ru.net.terracraft.chat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerDataListener implements Listener {

    private final TerraCraftChatPlugin plugin;

    public PlayerDataListener(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getIgnoreManager().load(player);
        plugin.getSkinHeadCache().refresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getIgnoreManager().unload(player);
        plugin.getReplyManager().clear(player);
        plugin.getChatModerator().clear(player);
        plugin.getThreadManager().clearActive(player);
        plugin.getSkinHeadCache().remove(player);
        plugin.getSocialSpyManager().disable(player);
    }
}