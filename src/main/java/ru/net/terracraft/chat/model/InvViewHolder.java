package ru.net.terracraft.chat.model;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InvViewHolder implements InventoryHolder {

    private final String ownerName;
    private Inventory inventory;

    public InvViewHolder(String ownerName) {
        this.ownerName = ownerName;
    }

    @Override
    public @Nullable Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getOwnerName() {
        return ownerName;
    }
}