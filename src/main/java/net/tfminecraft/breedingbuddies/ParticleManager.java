package net.tfminecraft.breedingbuddies;

import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public class ParticleManager {
	public static void heartParticles(Entity entity) {
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            double x = livingEntity.getLocation().getX();
            double y = livingEntity.getLocation().getY() + 1.0;
            double z = livingEntity.getLocation().getZ();
            
            livingEntity.getWorld().spawnParticle(Particle.HEART, x, y, z, 20, 0.5, 0.5, 0.5, 0.1);
        }
    }
	
	public static void cherryParticles(Entity entity) {
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            double x = livingEntity.getLocation().getX();
            double y = livingEntity.getLocation().getY() + 1.0;
            double z = livingEntity.getLocation().getZ();
            
            livingEntity.getWorld().spawnParticle(Particle.CHERRY_LEAVES, x, y, z, 10, 0.5, 0.5, 0.5, 0.1);
        }
    }
}
