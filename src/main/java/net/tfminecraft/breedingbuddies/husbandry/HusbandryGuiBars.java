package net.tfminecraft.breedingbuddies.husbandry;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class HusbandryGuiBars {

    static final int BAR_SLOTS = 5;

    private HusbandryGuiBars() {}

    static int yieldPercent(HusbandryAnimal animal) {
        if (animal == null) {
            return 0;
        }
        int effective = HusbandryConfig.effectiveGenetics(animal);
        int max = HusbandryConfig.maxGenetics();
        return (int) Math.round(100.0 * effective / max);
    }

    static int filledSegments(int value, int max) {
        if (max <= 0 || value <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(BAR_SLOTS, (int) Math.round(BAR_SLOTS * (double) value / max)));
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    static ItemStack barSegment(String label, int value, int max, boolean filled) {
        Material material = filled ? Material.GREEN_CONCRETE : Material.GRAY_CONCRETE;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + label);
            meta.setLore(List.of("§7" + value + "/" + max));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static void fillBar(org.bukkit.inventory.Inventory inv, int startSlot, String label, int value, int max) {
        int filled = filledSegments(value, max);
        for (int i = 0; i < BAR_SLOTS; i++) {
            inv.setItem(startSlot + i, barSegment(label, value, max, i < filled));
        }
    }
}
