package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.*;
import net.tfminecraft.breedingbuddies.configurables.CustomItems;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class BundleCollector implements Listener {
	private JavaPlugin plugin;
    private static FileConfiguration bundlesConfig;

    public static void setBundlesConfig(FileConfiguration bundlesConfig) {
    	BundleCollector.bundlesConfig = bundlesConfig;
	}
    
	public BundleCollector(BreedingBuddies plugin) {
        this.plugin = plugin;
    }
	@EventHandler
	public void onInteract(PlayerInteractEntityEvent event) {
		Player player = event.getPlayer();
		Entity entity = event.getRightClicked();
		ItemStack itemInHand = player.getInventory().getItemInMainHand();

		if (OwnershipManager.isOwner(player.getUniqueId(), entity.getUniqueId())
				&& event.getHand().equals(EquipmentSlot.HAND)
				&& itemInHand != null
				&& CustomItems.collectorsItem.equalsIgnoreCase(ItemUtils.getItemId(itemInHand))
				) {

			ConfigurationSection animalSection = bundlesConfig.getConfigurationSection(entity.getType().toString().toUpperCase());
			if (animalSection == null || !animalSection.getBoolean("bundlesEnabled", false)) return;

			if (entity instanceof AbstractHorse)
				event.setCancelled(true);
			if (canCollect(entity)) {
				FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId());
				animal.setLastRewardCollected(new Date());
				ItemStack bundle = generateBundle(player, entity);
				if (InventoryUtils.countEmptySlots(player) > 0) {
					player.getInventory().addItem(bundle);
					SoundManager.playAmethystStepSound(entity);
				} else {
					player.sendMessage(Messages.lacksSpace);
				}
			}
			event.setCancelled(true);
		}
	}

	private ItemStack generateBundle(Player player, Entity entity) {
		ItemStack bundle = null;
		String entityType = entity.getType().toString();
		String tier = calculateTier(entity);
		String itemType = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.type");
		if (itemType.equalsIgnoreCase("vanilla")) {
			String itemId = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.itemID");
			Material material = Material.matchMaterial(itemId);
            if (material != null) {
            	bundle = new ItemStack(material, 1);
            }
		} else if (itemType.equalsIgnoreCase("mmoitem")) {
			String mmoitemId = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.mmoitemID");
			String mmoitemType = bundlesConfig.getString(entityType + ".bundles." + tier + ".bundleItem.mmoitemType");
			bundle = ItemUtils.generateMMOItem(mmoitemType, mmoitemId, 1);
		}
		if (bundle == null) {
			bundle = new ItemStack(Material.DIRT, 1);
		}
		ItemUtils.storeDataInItem(bundle, entityType, plugin);
		return bundle;
	}

	public String calculateTier(Entity entity) {
		String type = entity.getType().toString();
		ConfigurationSection animalSection = bundlesConfig.getConfigurationSection(type);
		ConfigurationSection bundlesSection = animalSection.getConfigurationSection("bundles");

		List<String> tiers = new ArrayList<String>();
		for (String tier : bundlesSection.getKeys(false)) {
            tiers.add(tier);
        }
		int numberOfRewards = tiers.size();
		double rangePerTier = Numbers.maxFriendshipAndGenetics / (double) numberOfRewards;
		FarmAnimal farmAnimal = OwnershipManager.getAnimal(entity.getUniqueId());
		
		double friendshipPoints = farmAnimal.getFriendshipPoints();
	    double geneticPoints = farmAnimal.getGeneticPoints();
	    double averagePoints = (friendshipPoints + geneticPoints) / 2;
	    double disparity = Math.abs(friendshipPoints - geneticPoints);
	    double statsAverage = Math.max(0, averagePoints - disparity);
        
        int tierIndex = (int) (statsAverage / rangePerTier);
        tierIndex = Math.max(0, Math.min(tierIndex, numberOfRewards - 1));
        
        return tiers.get(tierIndex);
    }

	private  boolean canCollect(Entity entity) {
		FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId());
		if (animal != null && animal.canCollectReward()) {
			return true;
		}
		SoundManager.playAngryCowSound(entity);
		return false;
	}
}
