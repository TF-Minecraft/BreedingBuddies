package net.tfminecraft.breedingbuddies;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemUtilsTest extends PluginTestBase {

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void itemsAreIdentifiedByMmoItemsIdOrElseByMaterial() {
        assertEquals("TAMING_ITEM", ItemUtils.getItemId(mmoItem("TAMING_ITEM")));
        assertEquals("SHEARS", ItemUtils.getItemId(new ItemStack(Material.SHEARS)));
    }

    @Test
    void customNamesAreReadOnlyWhenPresent() {
        assertEquals("Daisy", ItemUtils.getCustomName(named(Material.PAPER, "Daisy")));
        assertNull(ItemUtils.getCustomName(new ItemStack(Material.PAPER)));
        assertFalse(ItemUtils.hasCustomName(null));
        assertFalse(ItemUtils.hasCustomName(new ItemStack(Material.AIR)));
    }

    @Test
    void pluginDataRoundTripsThroughAnItemAndHidesAttributes() {
        ItemStack item = new ItemStack(Material.PAPER);

        ItemUtils.storeDataInItem(item, "COW", plugin);

        assertEquals("COW", ItemUtils.getDataFromItem(item, plugin));
        assertTrue(item.getItemMeta().hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES));
        assertNull(ItemUtils.getDataFromItem(new ItemStack(Material.PAPER), plugin));
    }

    @Test
    void missingItemsOrDataAreIgnored() {
        ItemStack air = new ItemStack(Material.AIR);
        ItemStack paper = new ItemStack(Material.PAPER);

        ItemUtils.storeDataInItem(null, "COW", plugin);
        ItemUtils.storeDataInItem(paper, null, plugin);
        ItemUtils.storeDataInItem(air, "COW", plugin);

        assertNull(ItemUtils.getDataFromItem(paper, plugin));
        assertNull(ItemUtils.getDataFromItem(null, plugin));
        assertNull(ItemUtils.getDataFromItem(air, plugin));
    }

    @Test
    void mmoItemsAreBuiltThroughMmoItemsInTheRequestedAmount() {
        ItemStack built = new ItemStack(Material.BUNDLE);
        stubMmoItems(Map.of("PETS:COMMON_ANIMAL_BUNDLE", built));

        ItemStack item = ItemUtils.generateMMOItem("pets", "common_animal_bundle", 3);

        assertEquals(Material.BUNDLE, item.getType());
        assertEquals(3, item.getAmount());
    }

    @Test
    void unknownMmoItemsGiveNothing() {
        stubMmoItems(Map.of("PETS:COMMON_ANIMAL_BUNDLE", new ItemStack(Material.BUNDLE)));

        assertNull(ItemUtils.generateMMOItem("PETS", "MISSING", 1));
    }

    @Test
    void withoutBothPluginsEnabledAPlaceholderIsGivenAndTheErrorLogged() {
        MMOItems mmoItems = stubMmoItems(Map.of("PETS:COMMON_ANIMAL_BUNDLE", new ItemStack(Material.BUNDLE)));
        Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("MythicLib"));

        try (LogCapture log = new LogCapture(Bukkit.getLogger())) {
            ItemStack item = ItemUtils.generateMMOItem("PETS", "COMMON_ANIMAL_BUNDLE", 3);

            assertEquals(new ItemStack(Material.DIRT, 1), item);
            assertTrue(log.contains("requires MMOItems and MythicLib"));
        }
        verify(mmoItems, never()).getItem(any(Type.class), anyString());
        assertTrue(ItemUtils.pluginExists("MMOItems"));
        assertFalse(ItemUtils.pluginExists("MythicLib"));
        assertFalse(ItemUtils.pluginExists("Missing"));
    }
}
