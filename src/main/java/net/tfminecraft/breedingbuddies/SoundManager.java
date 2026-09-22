package net.tfminecraft.breedingbuddies;

import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class SoundManager {
	public static void playAmethystStepSound(Entity entity) {
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            livingEntity.getWorld().playSound(livingEntity.getLocation(), "block.amethyst_block.step", 1.0f, 1.0f);
        }
    }
	
	public static void playAmethystClusterStepSound(Entity entity) {
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            livingEntity.getWorld().playSound(livingEntity.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1.0f, 1.0f);
        }
    }
	
	public static void playAngryCowSound(Entity entity) {
        if (entity instanceof LivingEntity) {
        	String name = ("entity." + entity.getType() + ".ambient").toLowerCase();
            LivingEntity livingEntity = (LivingEntity) entity;
            livingEntity.getWorld().playSound(livingEntity.getLocation(), name, 1.0f, 1.0f);
        }
    }

    public static void playFoalScream(Entity entity) {
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            livingEntity.getWorld().playSound(livingEntity.getLocation(), Sound.ENTITY_HORSE_DEATH, 1.2f, 1.5f);
        }
    }
	
	public static void playBookOpenSound(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
    }
	
	public static void playExperienceSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }
	
	public static void playSignSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_HANGING_SIGN_PLACE, 1.0f, 1.0f);
    }
}
