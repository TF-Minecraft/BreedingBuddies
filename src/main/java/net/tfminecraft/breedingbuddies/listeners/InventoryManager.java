package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.*;
import net.tfminecraft.breedingbuddies.configurables.CustomItems;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class InventoryManager implements Listener {
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Animals)) return;

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        String itemInHandId = ItemUtils.getItemId(itemInHand);

        boolean isEmptyHandSneak = player.isSneaking() && itemInHand.getType() == Material.AIR;
        boolean isMountStatsItem = itemInHandId.equalsIgnoreCase(CustomItems.mountStatsItem);

        FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId());
        boolean isOwned = !(animal == null);
        boolean isFromPlayer = OwnershipManager.isOwner(player.getUniqueId(), entity.getUniqueId());
        boolean isUnowned = UnownedAnimalsManager.isUnowned(entity.getUniqueId());
        boolean playerIsStaff = player.hasPermission("breedingbuddies.use");
        boolean isBBAnimal = (isOwned || isUnowned);

        if (isOwned)
            DayChangeListener.changeDayOnInteract(entity, animal);

        if (isEmptyHandSneak && MountUtils.isFullStatMount(entity)) {

        } else if (isEmptyHandSneak && isBBAnimal) {
            if (isUnowned) {
                animal = UnownedAnimalsManager.getAnimal(entity.getUniqueId());
            }
            if (isFromPlayer || isUnowned || playerIsStaff) {
                openAnimalMenu(player, animal, entity);
            }
        } else if (isMountStatsItem && MountUtils.isFullStatMount(entity)) {
            if (isFromPlayer || isUnowned || playerIsStaff || !isBBAnimal) {
                event.setCancelled(true);
                if (!isOwned)
                    animal = new FarmAnimal(entity.getUniqueId(), 0, entity);
                openMountMenu(player, (LivingEntity) entity, animal);
            }
        } else if (isMountStatsItem && isBBAnimal) {
            if (isUnowned) {
                animal = UnownedAnimalsManager.getAnimal(entity.getUniqueId());
            }
            if (isFromPlayer || isUnowned || playerIsStaff) {
                openAnimalMenu(player, animal, entity);
            }
        }
    }
	
    public void openAnimalMenu(Player player, FarmAnimal farmAnimal, Entity entity) {
        String animalName = farmAnimal.getName();
        AnimalStates animalStatus = farmAnimal.getState();
        int friendshipPoints = farmAnimal.getFriendshipPoints();
        int geneticPoints = farmAnimal.getGeneticPoints();

        UUID entityUUID = entity.getUniqueId();
        AnimalInventoryHolder holder = new AnimalInventoryHolder(entityUUID);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.GOLD + Messages.inventoryName);

        // Primera fila: Nombre y Estado
        inv.setItem(3, createItem(Material.NAME_TAG, ChatColor.GOLD + "Name: " + animalName));
        inv.setItem(4, createItem(Material.CHEST, ChatColor.GOLD + "Next bundle: " + farmAnimal.timeForNextReward(entity)));
        inv.setItem(5, createItem(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "State: " + animalStatus.toString()));

        Set<UUID> owners = farmAnimal.getOwnersUuids();
        if (owners.contains(player.getUniqueId()))
            inv.setItem(26, createItem(Material.WRITABLE_BOOK, ChatColor.DARK_RED + "Remove ownership")); // Slot 36 = índice 35

        // Segunda fila: Puntos de amistad
        int maxFriendshipPoints = Numbers.maxFriendshipAndGenetics;
        int friendshipPerHeart = maxFriendshipPoints / 5; // 5 corazones en total
        for (int i = 0; i < 5; i++) {
            if (friendshipPoints >= (i + 1) * friendshipPerHeart) {
                inv.setItem(11 + i, createItem(Material.RED_DYE, ChatColor.RED + "Friendship: " + friendshipPoints + "/" + maxFriendshipPoints));
            } else {
                inv.setItem(11 + i, createItem(Material.GRAY_DYE, ChatColor.RED + "Friendship: " + friendshipPoints + "/" + maxFriendshipPoints));
            }
        }

        // Tercera fila: Puntos de genética
        int maxGeneticPoints = Numbers.maxFriendshipAndGenetics;
        int geneticPerHeart = maxGeneticPoints / 5;
        for (int i = 0; i < 5; i++) {
            if (geneticPoints >= (i + 1) * geneticPerHeart) {
                inv.setItem(20 + i, createItem(Material.SLIME_SPAWN_EGG, ChatColor.YELLOW + "Genetics: " + geneticPoints + "/" + maxGeneticPoints));
            } else {
                inv.setItem(20 + i, createItem(Material.VEX_SPAWN_EGG, ChatColor.YELLOW + "Genetics: " + geneticPoints + "/" + maxGeneticPoints));
            }
        }
        player.openInventory(inv);
    }

    public void openMountMenu(Player player, LivingEntity entity, FarmAnimal farmAnimal) {
        String animalName = farmAnimal.getName();
        AnimalStates animalStatus = farmAnimal.getState();
        int friendshipPoints = farmAnimal.getFriendshipPoints();
        int geneticPoints = farmAnimal.getGeneticPoints();
        int maxPoints = Numbers.maxFriendshipAndGenetics;
        int pointsPerHeart = maxPoints / 5;

        UUID entityUUID = entity.getUniqueId();
        AnimalInventoryHolder holder = new AnimalInventoryHolder(entityUUID);
        Inventory inv = Bukkit.createInventory(holder, 36, ChatColor.GOLD + Messages.inventoryName);

        // Fila 1: Nombre y estado (slots 3, 4, 5)
        inv.setItem(3, createItem(Material.NAME_TAG, ChatColor.GOLD + "Name: " + animalName));
        inv.setItem(4, createItem(Material.CHEST, ChatColor.GOLD + "Next bundle: " + farmAnimal.timeForNextReward(entity)));
        inv.setItem(5, createItem(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "State: " + animalStatus.toString()));
        inv.setItem(8, createItem(Material.SHEARS, ChatColor.GOLD + "Neutered: " + MountUtils.isNeutered(entity)));

        Set<UUID> owners = farmAnimal.getOwnersUuids();
        if (owners.contains(player.getUniqueId()))
            inv.setItem(35, createItem(Material.WRITABLE_BOOK, ChatColor.DARK_RED + "Remove ownership")); // Slot 36 = índice 35

        // Fila 2: Amistad (slots 11–15)
        for (int i = 0; i < 5; i++) {
            Material mat = friendshipPoints >= (i + 1) * pointsPerHeart ? Material.RED_DYE : Material.GRAY_DYE;
            inv.setItem(11 + i, createItem(mat, ChatColor.RED + "Friendship: " + friendshipPoints + "/" + maxPoints));
        }

        // Fila 3: Genética (slots 20–24)
        for (int i = 0; i < 5; i++) {
            Material mat = geneticPoints >= (i + 1) * pointsPerHeart ? Material.SLIME_SPAWN_EGG : Material.VEX_SPAWN_EGG;
            inv.setItem(20 + i, createItem(mat, ChatColor.YELLOW + "Genetics: " + geneticPoints + "/" + maxPoints));
        }

        // Fila 4: Stats de la montura (health, speed, jump en slots 29, 31, 33)
        double health = MountUtils.healthPointsToHearts(MountUtils.getMaxHealth(entity));
        double speed = MountUtils.speedToBlocksPerSecond(MountUtils.getSpeed(entity));
        double jump = MountUtils.jumpStrengthToBlockHeight(MountUtils.getJumpStrength(entity));

        inv.setItem(30, createItem(Material.GOLDEN_APPLE, ChatColor.LIGHT_PURPLE + "Health: " + String.format("%.2f", health)));
        inv.setItem(31, createItem(Material.FEATHER, ChatColor.AQUA + "Speed: " + String.format("%.2f", speed)));
        inv.setItem(32, createItem(Material.LEATHER_BOOTS, ChatColor.GREEN + "Jump: " + String.format("%.2f", jump)));

        player.openInventory(inv);
    }

    private void addStatBar(Inventory inv, int startSlot, double current, double max, Material filled, Material empty, String label) {
        int slots = 5;
        double valuePerSlot = max / (double) slots;

        for (int i = 0; i < slots; i++) {
            Material mat = current >= (i + 1) * valuePerSlot ? filled : empty;
            String display = label + ": " + String.format("%.2f", current) + "/" + String.format("%.2f", max);
            inv.setItem(startSlot + i, createItem(mat, display));
        }
    }

    private ItemStack createItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        item.setItemMeta(meta);
        return item;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AnimalInventoryHolder animalHolder) {
            event.setCancelled(true);
            UUID entityUUID = animalHolder.getEntityUUID();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.WRITABLE_BOOK) return;
            if (event.getWhoClicked() instanceof Player player) {
                FarmAnimal animal = OwnershipManager.getAnimal(entityUUID, player.getUniqueId());
                if (animal != null) {
                    OwnershipManager.removeOwnership(player.getUniqueId(), animal);
                    player.closeInventory();
                    player.sendMessage(String.format(Messages.removeOwnership, animal.getName()));
                    if (animal.getOwnersUuids().isEmpty()) {
                        animal.setState(AnimalStates.UNOWNED);
                        UnownedAnimalsManager.addUnownedAnimal(animal);
                    }
                }
            }
        }
    }
}
