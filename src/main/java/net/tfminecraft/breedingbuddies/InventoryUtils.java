package net.tfminecraft.breedingbuddies;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryUtils {

    public static int countEmptySlots(Player player) {
        Inventory inventory = player.getInventory();
        int emptyCount = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item == null) {
                emptyCount++;
            }
        }

        return emptyCount;
    }
}