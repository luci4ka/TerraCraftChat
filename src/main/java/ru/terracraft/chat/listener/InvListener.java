package ru.terracraft.chat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import ru.terracraft.chat.model.InvViewHolder;
import ru.terracraft.chat.TerraCraftChatPlugin;

public class InvListener implements Listener {

    private final TerraCraftChatPlugin plugin;

    public InvListener(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof InvViewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof InvViewHolder) {
            event.setCancelled(true);
        }
    }
}