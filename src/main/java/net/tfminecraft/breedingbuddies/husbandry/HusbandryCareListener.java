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
        if (hand.getType().isAir()) {
            pet(player, living, stored.get(), repository);
            return;
        }
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
        HusbandryTrust.Gesture gesture;
        if (feed) {
            if (animal.hungrySince() == null) {
                return;
            }
            HusbandrySimulator.clearHungry(animal, ThreadLocalRandom.current());
            HusbandryItems.useFromMainHand(player);
            gesture = HusbandryTrust.Gesture.FEED;
        } else {
            if (animal.dirtySince() == null) {
                return;
            }
            HusbandrySimulator.clearDirty(animal, ThreadLocalRandom.current());
            gesture = HusbandryTrust.Gesture.GLOVE;
        }
        HusbandryFx.playCare(living);
        // Staff can tend any animal, but only an owner's care builds its trust.
        if (HusbandryOwnershipService.isOwner(player, living.getUniqueId())) {
            HusbandryTrust.show(player, living, animal, HusbandryTrust.reward(animal, gesture, System.currentTimeMillis()));
        }
        repository.upsertAnimal(animal);
        HusbandryStateDisplay.sync(living, animal);
        HusbandryProfessionXp.tryGive(player, living.getType());
    }

    /** An empty-handed, standing right-click; mounts are petted by riding them (HusbandryMountListener). */
    private static void pet(Player player, LivingEntity living, HusbandryAnimal animal, HusbandryRepository repository) {
        if (player.isSneaking() || HusbandryMounts.isMount(living)
                || !HusbandryOwnershipService.isOwner(player, living.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean counted = HusbandryTrust.canPet(animal, now);
        int gained = HusbandryTrust.reward(animal, HusbandryTrust.Gesture.PET, now);
        if (counted) {
            repository.upsertAnimal(animal);
        }
        HusbandryTrust.show(player, living, animal, gained);
    }
}
