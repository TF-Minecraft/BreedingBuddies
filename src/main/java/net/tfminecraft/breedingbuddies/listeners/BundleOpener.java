package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.BreedingBuddies;
import net.tfminecraft.breedingbuddies.ItemUtils;
import net.tfminecraft.breedingbuddies.SoundManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BundleOpener implements Listener {
    private JavaPlugin plugin;
    private Random random = new Random();
    private static FileConfiguration bundlesConfig;
    
    public static void setBundlesConfig(FileConfiguration bundlesConfig) {
    	BundleOpener.bundlesConfig = bundlesConfig;
	}

    public BundleOpener(BreedingBuddies plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK) || event.getAction().equals(Action.RIGHT_CLICK_AIR)) {
            ItemStack itemInHand = event.getItem();
            Player player = event.getPlayer();
            String entityType = ItemUtils.getDataFromItem(itemInHand, plugin);
            if (entityType != null && itemInHand != null && isBundleItem(itemInHand, entityType, player)) {
                openBundle(player, itemInHand, entityType);
            }
        }
    }
    
    public boolean isBundleItem(ItemStack itemInHand, String entityType, Player player) {
        ConfigurationSection animalSection = bundlesConfig.getConfigurationSection(entityType);
    	if (bundlesConfig == null || animalSection == null) {
            return false;
        }

        ConfigurationSection bundlesSection = animalSection.getConfigurationSection("bundles");
        for (String tier : bundlesSection.getKeys(false)) {
    		String itemType = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.type");
        	if (itemType.equalsIgnoreCase("vanilla")) {
    			String itemId = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.itemID");
    			if (itemId != null && itemInHand != null && ItemUtils.getItemId(itemInHand).equalsIgnoreCase(itemId)) {
                    return true;
                }
    		} else if (itemType.equalsIgnoreCase("mmoitem")) {
    			String mmoitemId = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.mmoitemID");
    			if (mmoitemId != null && itemInHand != null && ItemUtils.getItemId(itemInHand).equalsIgnoreCase(mmoitemId)) {
                    return true;
                }
    		}
        }
        return false;
    }
    
    public void openBundle(Player player, ItemStack bundleItem, String entityType) {
        String tier = getTierFromItem(bundleItem, entityType);
        
        Map<String, ConfigurationSection> prizeMap = new HashMap<>();
        ConfigurationSection prizes = bundlesConfig.getConfigurationSection(entityType + ".bundles." + tier + ".prizes");
        if (prizes != null) {
            for (String key : prizes.getKeys(false)) {
                ConfigurationSection prizeConfig = prizes.getConfigurationSection(key);
                prizeMap.put(key, prizeConfig);
            }
        }
               
        ItemStack prize = selectPrize(prizeMap);
        bundleItem.setAmount(bundleItem.getAmount() - 1);
        player.getInventory().addItem(prize);
        SoundManager.playExperienceSound(player);
    }
    
    private String getTierFromItem(ItemStack bundleItem, String entityType) {
        for (String tier : bundlesConfig.getConfigurationSection(entityType + ".bundles.").getKeys(false)) {
        	String itemId = null;
        	if (bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.type").equalsIgnoreCase("vanilla")) {
        		itemId = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.itemID");
        	} else if (bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.type").equalsIgnoreCase("mmoitem")) {
        		itemId = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.mmoitemID");
        	}
            
            if (itemId != null && bundleItem != null && ItemUtils.getItemId(bundleItem).equalsIgnoreCase(itemId)) {
                return tier;
            }
        }
        return null;
    }
    
    private ItemStack selectPrize(Map<String, ConfigurationSection> prizeMap) {
        List<Map.Entry<String, ConfigurationSection>> shuffledPrizes = new ArrayList<>(prizeMap.entrySet());
        Collections.shuffle(shuffledPrizes);

        int totalWeight = 0;
        for (Map.Entry<String, ConfigurationSection> entry : shuffledPrizes) {
            totalWeight += entry.getValue().getInt("weight");
        }

        int value = random.nextInt(totalWeight) + 1;
        int weightSum = 0;

        for (Map.Entry<String, ConfigurationSection> entry : shuffledPrizes) {
            ConfigurationSection prize = entry.getValue();
            weightSum += prize.getInt("weight");
            if (value <= weightSum) {
                String type = prize.getString("type");
                String itemID = prize.getString("itemID");
                int amount = prize.getInt("amount");
                if ("vanilla".equalsIgnoreCase(type)) {
                    Material material = Material.matchMaterial(itemID);
                    if (material != null) {
                        return new ItemStack(material, amount);
                    }
                } else if ("mmoitem".equalsIgnoreCase(type)) {
                    String mmoitemType = prize.getString("mmoitemType");
                    String mmoitemID = prize.getString("mmoitemID");
                    ItemStack mmoitem = ItemUtils.generateMMOItem(mmoitemType, mmoitemID, amount);
                    if (mmoitem != null) {
                        return mmoitem;
                    }
                }
            }
        }
        return new ItemStack(Material.DIRT, 1);
    }
}