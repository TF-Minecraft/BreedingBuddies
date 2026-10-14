package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class HusbandryDeathListener implements Listener {

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }

        if (HusbandryConfig.isRemoveUnowned(entity.getType())
                && !HusbandryOwnershipService.hasAnyOwner(entity.getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            repository.deleteAnimal(entity.getUniqueId());
            HusbandryEntities.evict(entity.getUniqueId());
            HusbandryStateDisplay.removeAll(entity);
            return;
        }

        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(entity.getUniqueId());
        if (stored.isEmpty()) {
            return;
        }
        HusbandryAnimal animal = stored.get();
        HusbandrySpecies species = HusbandryConfig.species(entity.getType());
        long now = System.currentTimeMillis();
        boolean hasSlaughter = species != null && species.canSlaughter();
        boolean mature = HusbandryGrowth.isMature(animal, now);
        if (HusbandrySlaughterDrops.shouldReplaceVanilla(hasSlaughter, mature)) {
            ItemStack roast = HusbandryHarvest.buildFood(animal, species.slaughterMeat());
            if (roast == null || roast.getType().isAir()) {
                Bukkit.getLogger().warning("[BreedingBuddies] Slaughter roast failed for "
                        + entity.getType() + " using " + species.slaughterMeat());
            } else {
                event.getDrops().removeIf(drop -> !HusbandryHarvest.isWoolDrop(drop));
                event.getDrops().add(roast);
                HusbandryDropRoller.rollSlaughterExtras(animal, ThreadLocalRandom.current(), now)
                        .forEach(extra -> event.getDrops().add(extra));
            }
        }
        repository.deleteAnimal(entity.getUniqueId());
        HusbandryEntities.evict(entity.getUniqueId());
        HusbandryStateDisplay.removeAll(entity);
    }
}
