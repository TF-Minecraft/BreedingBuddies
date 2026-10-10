package net.tfminecraft.breedingbuddies;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class AnimalInventoryHolder implements InventoryHolder {
    private final UUID entityUUID;
    private final Inventory inventory;

    public AnimalInventoryHolder(UUID entityUUID, int size, String title) {
        this.entityUUID = entityUUID;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
