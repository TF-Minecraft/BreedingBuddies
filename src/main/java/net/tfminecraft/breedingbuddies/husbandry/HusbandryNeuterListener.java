package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class HusbandryNeuterListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!HusbandryItems.matches(hand, HusbandryConfig.neuterItem())) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity living)) {
            return;
        }
        event.setCancelled(true);
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(living.getUniqueId());
        if (stored.isEmpty()) {
            player.sendMessage("§cThis animal cannot be neutered.");
            return;
        }
        if (!HusbandryOwnershipService.isOwner(player, living.getUniqueId())
                && !HusbandryOwnershipService.isStaff(player)) {
            player.sendMessage("§cThis is not your animal.");
            return;
        }
        HusbandryAnimal animal = stored.get();
        if (animal.neutered()) {
            player.sendMessage("§eThis animal is already neutered.");
            return;
        }
        animal.setNeutered(true);
        repository.upsertAnimal(animal);
        HusbandryItems.useFromMainHand(player);
        HusbandryFx.playNeuter(living);
        player.sendMessage("§aNeutered.");
    }
}
