package net.tfminecraft.breedingbuddies.husbandry;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

final class HusbandryShed {

    private HusbandryShed() {}

    static void tryShed(LivingEntity entity, HusbandryAnimal animal, long nowMillis) {
        if (entity == null || animal == null) {
            return;
        }
        if (!HusbandryGrowth.isMature(animal, nowMillis) || !HusbandrySimulator.isHappy(animal)) {
            return;
        }
        org.bukkit.entity.EntityType type;
        try {
            type = org.bukkit.entity.EntityType.valueOf(animal.type());
        } catch (IllegalArgumentException ex) {
            return;
        }
        HusbandrySpecies species = HusbandryConfig.species(type);
        if (species == null || !species.canShed()) {
            return;
        }
        Long readyAt = animal.shedReadyAt();
        if (readyAt != null && readyAt > nowMillis) {
            return;
        }
        if (Math.random() >= HusbandryConfig.shedChance()) {
            animal.setShedReadyAt(nowMillis + HusbandryConfig.shedTimerSeconds() * 1000L);
            return;
        }
        for (ItemStack stack : HusbandryDropRoller.rollShedDrops(animal, ThreadLocalRandom.current())) {
            if (stack != null) {
                entity.getWorld().dropItemNaturally(entity.getLocation(), stack);
            }
        }
        animal.setShedReadyAt(nowMillis + HusbandryConfig.shedTimerSeconds() * 1000L);
    }
}
