package net.tfminecraft.breedingbuddies;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class AnimalInventoryHolder implements InventoryHolder {
    private final UUID entityUUID;

    public AnimalInventoryHolder(UUID entityUUID) {
        this.entityUUID = entityUUID;
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }
    @Override
    public Inventory getInventory() {
        return null;
    }
}
