package net.tfminecraft.breedingbuddies;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemUtils {
    private ItemUtils() {
    }

	public static String getItemId(ItemStack item) {
		NBTItem nbtItem = NBTItem.get(item);
		if (nbtItem.hasType()) {
			return nbtItem.getString("MMOITEMS_ITEM_ID");
		}
		return item.getType().toString();
	}
	
	public static boolean hasCustomName(ItemStack item) {
	    if (item != null && item.hasItemMeta()) {
	        ItemMeta meta = item.getItemMeta();
	        return meta != null && meta.hasDisplayName();
	    }
	    return false;
	}
	
	public static String getCustomName(ItemStack item) {
	    if (hasCustomName(item)) {
	        ItemMeta meta = item.getItemMeta();
	        return meta.getDisplayName();
	    }
	    return null;
	}

	public static void storeDataInItem(ItemStack item, String data, JavaPlugin plugin) {
	    if (item != null && data != null) {
	        ItemMeta meta = item.getItemMeta();
	        if (meta != null) {
	            NamespacedKey key = new NamespacedKey(plugin, "bb_data");
	            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, data);
	            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
	            item.setItemMeta(meta);
	        }
	    }
	}
	
	public static String getDataFromItem(ItemStack item, JavaPlugin plugin) {
	    if (item != null) {
	        ItemMeta meta = item.getItemMeta();
	        if (meta != null) {
	            NamespacedKey key = new NamespacedKey(plugin, "bb_data");
	            String data = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
	            if (data != null) {
	                return data;
	            }
	        }
	    }
	    return null;
	}
	
	@SuppressWarnings("deprecation")
	public static ItemStack generateMMOItem(String type, String id, int amount) {
		if (!(pluginExists("MMOItems") && pluginExists("MythicLib"))) {
			Bukkit.getLogger().info("[BreedingBuddies] ERROR! This operation requires MMOItems and MythicLib!");
			return new ItemStack(Material.DIRT, 1);
		}
		
	    // Null when MMOItems has no such item; callers fall back to a placeholder.
	    ItemStack item = MMOItems.plugin.getItem(MMOItems.plugin.getTypes().get(type.toUpperCase()), id.toUpperCase());
	    if (item != null) {
	        item.setAmount(amount);
	    }
	    return item;
	}
	
	public static boolean pluginExists(String pluginName) {
		Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
	}
}