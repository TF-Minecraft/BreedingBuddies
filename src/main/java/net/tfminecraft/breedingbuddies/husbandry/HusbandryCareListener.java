package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class HusbandryCareListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity living)) {
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(living.getUniqueId());
        if (stored.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean feed = HusbandryItems.matches(hand, HusbandryConfig.feedItem());
        boolean glove = HusbandryItems.matches(hand, HusbandryConfig.gloveItem());
        if (!feed && !glove) {
            return;
        }

        event.setCancelled(true);
        if (!HusbandryOwnershipService.isOwner(player, living.getUniqueId())
                && !HusbandryOwnershipService.isStaff(player)) {
            player.sendMessage("§cThis is not your animal.");
            return;
        }
        HusbandryAnimal animal = stored.get();
        if (feed) {
            if (animal.hungrySince() == null) {
                return;
            }
            HusbandrySimulator.clearHungry(animal, ThreadLocalRandom.current());
            HusbandryItems.useFromMainHand(player);
            HusbandryFx.playCare(living);
        } else {
            if (animal.dirtySince() == null) {
                return;
            }
            HusbandrySimulator.clearDirty(animal, ThreadLocalRandom.current());
            HusbandryFx.playCare(living);
        }
        repository.upsertAnimal(animal);
        HusbandryStateDisplay.sync(living, animal);
        HusbandryProfessionXp.tryGive(player, living.getType());
    }
}
