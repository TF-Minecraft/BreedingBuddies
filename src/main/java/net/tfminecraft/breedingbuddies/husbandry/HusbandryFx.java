package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

final class HusbandryFx {

    private HusbandryFx() {}

    static void playCare(LivingEntity entity) {
        entity.getWorld().spawnParticle(
                Particle.FIREWORK,
                entity.getLocation().add(0, entity.getHeight() * 0.7, 0),
                12,
                0.35,
                0.35,
                0.35,
                0.02);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
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
        entity.getWorld().spawnParticle(
                Particle.SPORE_BLOSSOM_AIR,
                entity.getLocation().add(0, entity.getHeight() * 0.7, 0),
                12,
                0.35,
                0.35,
                0.35,
                0.02);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
    }
}
