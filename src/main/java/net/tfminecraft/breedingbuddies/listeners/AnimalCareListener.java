package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.configurables.CustomItems;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.ItemUtils;
import net.tfminecraft.breedingbuddies.OwnershipManager;
import net.tfminecraft.breedingbuddies.ParticleManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class AnimalCareListener implements Listener {
	@EventHandler
	public void onInteract(PlayerInteractEntityEvent event) {
		Player player = event.getPlayer();
		Entity entity = event.getRightClicked();
		ItemStack itemInHand = player.getInventory().getItemInMainHand();
		
		if(event.getHand().equals(EquipmentSlot.HAND)) {
			if (OwnershipManager.isOwner(player.getUniqueId(), entity.getUniqueId())) {
				FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId(), player.getUniqueId());
				if (animal == null) return;
				if (ItemUtils.getItemId(itemInHand).equalsIgnoreCase(CustomItems.universalFeed)) {
					DayChangeListener.changeDayOnInteract(entity, animal);
					feed(animal, entity, player, itemInHand);
					event.setCancelled(true);
				} else if (ItemUtils.getItemId(itemInHand).equalsIgnoreCase(CustomItems.caringItem)) {
					DayChangeListener.changeDayOnInteract(entity, animal);
					care(animal, entity, player, itemInHand);
					event.setCancelled(true);
				}
			} else if (OwnershipManager.hasOwner(entity.getUniqueId())
					&& !OwnershipManager.isOwner(player.getUniqueId(), entity.getUniqueId())) {
				if (!player.hasPermission("breedingbuddies.use")) {
					player.sendMessage(Messages.notOwner);
					event.setCancelled(true);
				}
			}
		}
	}
	
	public void feed(FarmAnimal animal, Entity entity, Player player, ItemStack itemInHand) {
		if (animal.getFed()) {
			player.sendMessage(String.format(Messages.alreadyFed, animal.getName()));
		} else {
			itemInHand.setAmount(itemInHand.getAmount() - 1);
			animal.increaseFriendship(Numbers.friendshipByFeeding);
			animal.setFed(true);
			ParticleManager.heartParticles(entity);
		}
	}
	
	public void care(FarmAnimal animal, Entity entity, Player player, ItemStack itemInHand) {
		if (animal.getCared()) {
			player.sendMessage(String.format(Messages.alreadyCared, animal.getName()));
		} else {
//			itemInHand.setAmount(itemInHand.getAmount() - 1);
			animal.increaseFriendship(Numbers.friendshipByCaring);
			animal.setCared(true);
			ParticleManager.heartParticles(entity);
		}
	}
}
