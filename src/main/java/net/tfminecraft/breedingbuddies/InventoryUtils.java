package net.tfminecraft.breedingbuddies;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InventoryUtils {
    private InventoryUtils() {
    }

    public static int countEmptySlots(Player player) {
        int emptyCount = 0;

        // Armour and off-hand slots cannot receive items from addItem().
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                emptyCount++;
            }
        }

        return emptyCount;
    }
}