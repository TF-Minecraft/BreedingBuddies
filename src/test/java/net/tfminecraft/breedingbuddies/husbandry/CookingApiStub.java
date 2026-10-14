package net.tfminecraft.breedingbuddies.husbandry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

import java.util.Map;
import java.util.OptionalInt;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.mockito.MockedStatic;

import net.tfminecraft.cooking.api.CookingFoodApi;

/** Stands in for Cooking's food API so tests never initialise Cooking internals. */
final class CookingApiStub implements AutoCloseable {
    /** Where these tests store an item's stars, as Cooking does. */
    static final NamespacedKey QUALITY = new NamespacedKey("cooking", "quality");

    final MockedStatic<CookingFoodApi> api = mockStatic(CookingFoodApi.class);

    CookingApiStub() {
        api.when(() -> CookingFoodApi.qualityOf(any())).thenAnswer(call -> stars(call.getArgument(0)));
        api.when(() -> CookingFoodApi.addToInventory(any(), any())).thenAnswer(call -> {
            Player player = call.getArgument(0);
            Map<Integer, ItemStack> left = player.getInventory().addItem((ItemStack) call.getArgument(1));
            return left.isEmpty() ? null : left.values().iterator().next();
        });
        api.when(() -> CookingFoodApi.buildMilk(any(), anyInt())).thenAnswer(call -> new ItemStack(Material.MILK_BUCKET));
    }

    private static OptionalInt stars(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return OptionalInt.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        Integer stored = meta == null ? null : meta.getPersistentDataContainer().get(QUALITY, PersistentDataType.INTEGER);
        return stored == null ? OptionalInt.empty() : OptionalInt.of(Math.max(1, Math.min(5, stored)));
    }

    @Override
    public void close() {
        api.close();
    }
}
