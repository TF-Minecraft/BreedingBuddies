package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.TLibs;

public final class HusbandryItems {

    private HusbandryItems() {}

    public static boolean matches(ItemStack stack, String path) {
        if (stack == null || path == null || path.isBlank()) {
            return false;
        }
        return TLibs.getItemAPI().getChecker().checkItemWithPath(stack, path);
    }

    /** Applies one use to the player's main-hand item and writes it back to the inventory. */
    public static void useFromMainHand(Player player) {
        if (player == null) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(applyUse(hand));
    }

    public static void consumeOne(ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
    }

    static ItemStack applyUse(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        if (stack.getItemMeta() instanceof Damageable damageable
                && stack.getType().getMaxDurability() > 0
                && !stack.getItemMeta().isUnbreakable()) {
            int nextDamage = damageable.getDamage() + 1;
            if (nextDamage >= stack.getType().getMaxDurability()) {
                return null;
            }
            damageable.setDamage(nextDamage);
            stack.setItemMeta(damageable);
            return stack;
        }
        if (stack.getAmount() <= 1) {
            return null;
        }
        stack.setAmount(stack.getAmount() - 1);
        return stack;
    }

    public static String tameName(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String stored = stack.getItemMeta().getPersistentDataContainer()
                .get(HusbandryKeys.TAME_NAME, PersistentDataType.STRING);
        if (stored == null || stored.isBlank() || "???".equals(stored.trim())) {
            return null;
        }
        return stored.trim();
    }

    public static void setTameName(ItemStack stack, String name) {
        if (stack == null || name == null || name.isBlank()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(HusbandryKeys.TAME_NAME, PersistentDataType.STRING, name.trim());
        stack.setItemMeta(meta);
    }

    public static String linkedAnimal(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(HusbandryKeys.LINKED_ANIMAL, PersistentDataType.STRING);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void setLinkedAnimal(ItemStack stack, String uuid, String loreLine) {
        if (stack == null || uuid == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(HusbandryKeys.LINKED_ANIMAL, PersistentDataType.STRING, uuid);
        if (loreLine != null && !loreLine.isBlank()) {
            meta.setLore(java.util.List.of(loreLine));
        }
        stack.setItemMeta(meta);
    }
}
