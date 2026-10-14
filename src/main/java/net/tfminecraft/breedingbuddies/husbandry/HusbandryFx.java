package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;

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
