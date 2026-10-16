package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

final class HusbandryFx {

    private HusbandryFx() {}

    /** Feeding or brushing; the hearts for any trust it earns come from HusbandryTrust. */
    static void playCare(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0, entity.getHeight() * 0.7, 0),
                8, 0.35, 0.35, 0.35, 0.0);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.8f, 1.2f);
    }

    /** The original BreedingBuddies flourish: cherry petals and a chime, for taming and sharing. */
    static void playBond(org.bukkit.entity.Entity entity) {
        entity.getWorld().spawnParticle(Particle.CHERRY_LEAVES, entity.getLocation().add(0, entity.getHeight() * 0.7, 0),
                10, 0.5, 0.5, 0.5, 0.1);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1f, 1f);
    }

    /** A foal's cry for horses, as BreedingBuddies had; a short yelp for other animals. */
    static void playNeuter(LivingEntity entity) {
        Sound cry = entity instanceof org.bukkit.entity.AbstractHorse ? Sound.ENTITY_HORSE_DEATH : Sound.ENTITY_GENERIC_HURT;
        entity.getWorld().playSound(entity.getLocation(), cry, 1.2f, 1.5f);
    }

    /** A pig roots in the dirt, a chicken moults, a cow just leaves something for the fields. */
    static void playShed(LivingEntity entity) {
        org.bukkit.Location at = entity.getLocation();
        switch (entity.getType()) {
            case PIG -> {
                entity.getWorld().spawnParticle(Particle.BLOCK, at, 16, 0.3, 0.05, 0.3, Material.DIRT.createBlockData());
                entity.getWorld().playSound(at, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.8f, 1f);
            }
            case CHICKEN -> {
                entity.getWorld().spawnParticle(Particle.ITEM, at.clone().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2, 0.02,
                        new ItemStack(Material.FEATHER));
                entity.getWorld().playSound(at, Sound.ENTITY_CHICKEN_AMBIENT, 0.7f, 1.2f);
            }
            default -> entity.getWorld().playSound(at, Sound.BLOCK_MUD_PLACE, 0.6f, 0.9f);
        }
    }

    static void playTame(LivingEntity entity) {
        playBond(entity);
    }
}
