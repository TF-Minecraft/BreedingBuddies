package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.*;
import net.tfminecraft.breedingbuddies.configurables.CustomItems;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.spigotmc.event.entity.EntityMountEvent;

public class MountListener implements Listener {
    @EventHandler
    public void onMount(EntityMountEvent event) {
        Entity entity =  event.getMount();
        Entity rider = event.getEntity();

        if (!(rider instanceof Player)) {
            return;
        }

        Player player = (Player) rider;
        if (entity instanceof Animals) {
            //se cancela el evento si el caballo tiene dueño y no es el que interacciona
            if (OwnershipManager.hasOwner(entity.getUniqueId()) && !OwnershipManager.isOwner(player.getUniqueId(), entity.getUniqueId())) {
                SoundManager.playAngryCowSound(entity);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if(event.getHand().equals(EquipmentSlot.HAND)) {
            if (MountUtils.isFullStatMount(entity) && ItemUtils.getItemId(itemInHand).equalsIgnoreCase(CustomItems.neuterItem)) {
                if (OwnershipManager.isOwner(player.getUniqueId(), entity.getUniqueId()) || player.hasPermission("breedingbuddies.use")) {
                    AbstractHorse abstractHorse = (AbstractHorse) entity;
                    FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId(), player.getUniqueId());

                    if (animal != null && !MountUtils.isNeutered(entity)) {
                        if (!abstractHorse.isAdult() || player.hasPermission("breedingbuddies.use")) {
                            SoundManager.playFoalScream(entity);
                            MountUtils.setNeuteredFlag(entity, true);
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

    //BORRAR
    @EventHandler
    public void onInteractFix(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();

        if(event.getHand().equals(EquipmentSlot.HAND)) {
            FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId());
            if (MountUtils.isFullStatMount(entity) && animal != null) {
                animal.setStableNeeded(false);
            }
        }
    }

    //BORRAR
    @EventHandler
    public void onMountFix(EntityMountEvent event) {
        Entity entity =  event.getMount();
        Entity rider = event.getEntity();

        if (!(rider instanceof Player)) {
            return;
        }

        Player player = (Player) rider;
        if (entity instanceof Animals) {
            FarmAnimal animal = OwnershipManager.getAnimal(entity.getUniqueId(), player.getUniqueId());
            if (animal != null) {
                animal.setStableNeeded(false);
            }
        }
    }
}
