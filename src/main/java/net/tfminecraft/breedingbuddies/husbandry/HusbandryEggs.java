package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

final class HusbandryEggs {

    private HusbandryEggs() {}

    // Cold and warm chicken variants lay blue and brown eggs.
    static boolean isVanillaEgg(Material material) {
        return material == Material.EGG || material == Material.BLUE_EGG || material == Material.BROWN_EGG;
    }

    static void tryLay(LivingEntity entity, HusbandryAnimal animal, long nowMillis) {
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
        if (species == null || !species.hasEgg()) {
            return;
        }
        Long readyAt = animal.eggReadyAt();
        if (readyAt != null && readyAt > nowMillis) {
            return;
        }
        ItemStack stack = buildEgg(animal, species);
        if (stack != null) {
            entity.getWorld().dropItemNaturally(entity.getLocation(), stack);
        }
        animal.setEggReadyAt(nowMillis + HusbandryConfig.eggTimerSeconds() * 1000L);
    }

    private static ItemStack buildEgg(HusbandryAnimal animal, HusbandrySpecies species) {
        String egg = species.egg();
        if (species.vanillaEggs()) {
            return new ItemStack(Material.EGG);
        }
        if (egg.startsWith("food(")) {
            return HusbandryHarvest.buildFood(animal, egg);
        }
        return HusbandryHarvest.buildTlibs(egg, 1);
    }
}
