package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.*;
import net.tfminecraft.breedingbuddies.configurables.CustomItems;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Tameable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TamingListener implements Listener {
	private JavaPlugin plugin;

	public TamingListener(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onRightClick(PlayerInteractEvent event) {
		if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK) || event.getAction().equals(Action.RIGHT_CLICK_AIR)) {
			ItemStack itemInHand = event.getItem();
			Player player = event.getPlayer();
			if (itemInHand != null) {
				String itemInHandId = ItemUtils.getItemId(itemInHand);
				if (itemInHandId.equalsIgnoreCase(CustomItems.coownershipItem)) {
					handleEmptyInteraction(player, itemInHand);
				}
			}
		}
	}
	@EventHandler
	public void onInteract(PlayerInteractEntityEvent event) {
		Player player = event.getPlayer();
		Entity entity = event.getRightClicked();
		ItemStack itemInHand = player.getInventory().getItemInMainHand();

		/** PARA VER SI ES UN MYTHICMOB
		 * ActiveMob mythicMob = MythicBukkit.inst().getAPIHelper().getMythicMobInstance(entity);
		 * if (mythicMob == null) {
		 * return;
		 * }
		 * MYTHICMOB ID
		 * String mobId = mythicMob.getType().getInternalName();
		 */

		String itemInHandId = ItemUtils.getItemId(itemInHand);
		if (itemInHandId.equalsIgnoreCase(CustomItems.tamingItem) || itemInHandId.equalsIgnoreCase(CustomItems.coownershipItem))
		{
			event.setCancelled(true);
			if (entity instanceof Tameable) {
				Tameable tameable = (Tameable) entity;
				if (!tameable.isTamed()) {
					player.sendMessage(Messages.tameFirst);
					return;
				}
			}
		}

		if (event.getHand().equals(EquipmentSlot.HAND)) {
			if (AnimalUtils.isFarmAnimal(entity)) {
				handleFarmAnimalInteraction(player, entity, itemInHand);
			} else if (entity instanceof Player && ItemUtils.getItemId(itemInHand).equalsIgnoreCase(CustomItems.coownershipItem)) {
				handlePlayerInteraction(player, entity, itemInHand);
			}
		}
	}

	private void handleEmptyInteraction(Player player, ItemStack itemInHand) {
		String entityUuid = ItemUtils.getDataFromItem(itemInHand, plugin);
		UUID uuid = UUID.fromString(entityUuid);
		FarmAnimal animal = OwnershipManager.getAnimal(uuid);
		if (animal == null) {
			player.sendMessage(Messages.invalidAnimalOrNotFound);
			return;
		}
		if (entityUuid != null) {
			if (OwnershipManager.isOwner(player.getUniqueId(), uuid)) {
				player.sendMessage(String.format(Messages.alreadyOwner, animal.getName()));
			} else {
				OwnershipManager.registerOwnership(player.getUniqueId(), animal);
//				targetPlayer.sendMessage(String.format(Messages.nowCoowner, animal.getName()));
//				player.sendMessage(String.format(Messages.ownershipShared, animal.getName()));
				SoundManager.playAmethystClusterStepSound(player);
				ParticleManager.cherryParticles(player);
				itemInHand.setAmount(itemInHand.getAmount() - 1);
			}
		} else {
			player.sendMessage(Messages.useOnOwnedAnimal);
		}
	}

	private void handlePlayerInteraction(Player player, Entity targetPlayer, ItemStack itemInHand) {
		String entityUuid = ItemUtils.getDataFromItem(itemInHand, plugin);
		UUID uuid = UUID.fromString(entityUuid);
		FarmAnimal animal = OwnershipManager.getAnimal(uuid);
		if (animal == null) {
		    player.sendMessage(Messages.invalidAnimalOrNotFound);
		    return;
		}
		if (entityUuid != null) {
			if (OwnershipManager.isOwner(targetPlayer.getUniqueId(), uuid)) {
				player.sendMessage(String.format(Messages.alreadyOwner, animal.getName()));
			} else {
				OwnershipManager.registerOwnership(targetPlayer.getUniqueId(), animal);
//				targetPlayer.sendMessage(String.format(Messages.nowCoowner, animal.getName()));
//				player.sendMessage(String.format(Messages.ownershipShared, animal.getName()));
				SoundManager.playAmethystClusterStepSound(targetPlayer);
				ParticleManager.cherryParticles(targetPlayer);
				itemInHand.setAmount(itemInHand.getAmount() - 1);
			}
		} else {
			player.sendMessage(Messages.useOnOwnedAnimal);
		}
	}

	private void handleFarmAnimalInteraction(Player player, Entity entity, ItemStack itemInHand) {
		String itemInHandId = ItemUtils.getItemId(itemInHand);

		if (itemInHandId.equalsIgnoreCase(CustomItems.tamingItem)) {
			processTamingItem(player, entity, itemInHand);
		} else if (itemInHandId.equalsIgnoreCase(CustomItems.coownershipItem)) {
			processCoownershipItem(player, entity, itemInHand);
		}
	}

	private void processCoownershipItem(Player player, Entity entity, ItemStack itemInHand) {
		String itemData = ItemUtils.getDataFromItem(itemInHand, plugin);
		if (!OwnershipManager.hasOwner(entity.getUniqueId())) {
			player.sendMessage(Messages.ownerFirst);
		} else if (itemData != null && entity.getUniqueId().toString().equals(itemData)) {
			player.sendMessage(String.format(Messages.alreadyLinked, entity.getCustomName()));
		} else if (OwnershipManager.isOwner(player.getUniqueId(), entity.getUniqueId())) {
			ItemUtils.storeDataInItem(itemInHand, entity.getUniqueId().toString(), plugin);
			changeTokenLore(entity, itemInHand);
			SoundManager.playAmethystStepSound(entity);
			ParticleManager.cherryParticles(entity);
		} else {
			player.sendMessage(String.format(Messages.onlyOwnerCanShare, entity.getCustomName()));
		}
	}

	public ItemStack changeTokenLore(Entity entity, ItemStack itemBase) {
		if (entity == null || itemBase == null || itemBase.getType().isAir()) return itemBase;

		String nombreEntidad = null;
		if (entity instanceof LivingEntity living) {
			if (living.getCustomName() != null && !living.getCustomName().isEmpty()) {
				nombreEntidad = ChatColor.stripColor(living.getCustomName()); // Limpia colores si los hubiera
			}
		}

		String tipo = formatearTipo(entity.getType().name());

		String loreLine;
		if (nombreEntidad != null) {
			loreLine = ChatColor.GRAY + "This token is linked to " + nombreEntidad + " the " + tipo + ".";
			ItemMeta meta = itemBase.getItemMeta();
			if (meta != null) {
				List<String> lore = new ArrayList<>();
				lore.add(loreLine);
				meta.setLore(lore);
				itemBase.setItemMeta(meta);
			}
		}
		return itemBase;
	}


	// Convierte ENTITY_TYPE en "Entity Type" o "Cow", "Zombie Villager", etc.
	private String formatearTipo(String typeName) {
		String[] palabras = typeName.toLowerCase().split("_");
		StringBuilder resultado = new StringBuilder();
		for (String palabra : palabras) {
			resultado.append(Character.toUpperCase(palabra.charAt(0)))
					.append(palabra.substring(1))
					.append(" ");
		}
		return resultado.toString().trim(); // "Cow", "Zombie Villager", etc.
	}

	private void processTamingItem(Player player, Entity entity, ItemStack itemInHand) {
		boolean hasOwner = OwnershipManager.hasOwner(entity.getUniqueId());
		boolean playerIsOwner = OwnershipManager.isOwner(player.getUniqueId(), entity.getUniqueId());
		if (hasOwner && playerIsOwner) {
			FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId(), player.getUniqueId());

			if (animal != null && name(entity, itemInHand, animal)) {
				SoundManager.playAmethystClusterStepSound(entity);
			} else {
				player.sendMessage(Messages.nameFirst);
			}
		} else if (hasOwner) {
			player.sendMessage(Messages.notOwner);
        } else {
			if (name(entity, itemInHand)) {
				tame(player, entity);
				player.sendMessage(String.format(Messages.nowOwner, entity.getCustomName()));
				SoundManager.playAmethystClusterStepSound(entity);
				ParticleManager.cherryParticles(entity);
			} else {
				player.sendMessage(Messages.nameFirst);
			}
		}
	}

	public void tame(Player player, Entity entity) {
		FarmAnimal newAnimal;
		if (UnownedAnimalsManager.isUnowned(entity.getUniqueId()) && UnownedAnimalsManager.getAnimal(entity.getUniqueId()).getState() == AnimalStates.SPAWNED) {
			FarmAnimal oldAnimal = UnownedAnimalsManager.getAnimal(entity.getUniqueId());
			int friendships = oldAnimal.getFriendshipPoints();
			int genetics = oldAnimal.getGeneticPoints();
			UnownedAnimalsManager.removeUnownedAnimal(entity.getUniqueId());
			newAnimal = new FarmAnimal(entity.getUniqueId(), entity.getCustomName(), player.getUniqueId(), entity, genetics, friendships);
		} else if (UnownedAnimalsManager.isUnowned(entity.getUniqueId())) {
			int genetics = UnownedAnimalsManager.getAnimal(entity.getUniqueId()).getGeneticPoints();
			UnownedAnimalsManager.removeUnownedAnimal(entity.getUniqueId());
			newAnimal = new FarmAnimal(entity.getUniqueId(), entity.getCustomName(), player.getUniqueId(), entity, genetics);
		} else {
			newAnimal = new FarmAnimal(entity.getUniqueId(), entity.getCustomName(), player.getUniqueId(), entity);
		}
		OwnershipManager.registerOwnership(player.getUniqueId(), newAnimal);
		newAnimal.setState(AnimalStates.HAPPY);
	}

	public boolean name(Entity entity, ItemStack itemInHand) {
		String customName = ItemUtils.getCustomName(itemInHand);
		if (customName == null || ItemUtils.getDataFromItem(itemInHand, plugin) == null) {
			return false;
		} else {
			entity.setCustomName(customName);
			entity.setCustomNameVisible(false);
			itemInHand.setAmount(itemInHand.getAmount() - 1);
			return true;
		}
	}

	public boolean name(Entity entity, ItemStack itemInHand, FarmAnimal animal) {
		String customName = ItemUtils.getCustomName(itemInHand);
		if (customName == null || ItemUtils.getDataFromItem(itemInHand, plugin) == null) {
			return false;
		} else {
			entity.setCustomName(customName);
			entity.setCustomNameVisible(false);
			animal.setName(customName);
			itemInHand.setAmount(itemInHand.getAmount() - 1);
			return true;
		}
	}
}
