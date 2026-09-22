package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.*;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import net.tfminecraft.breedingbuddies.configurables.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;

import java.util.Random;

public class BreedingListener implements Listener {
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityBreed(EntityBreedEvent event) {
		if (event.getBreeder() instanceof Player) {
			Player player = (Player) event.getBreeder();
			Entity motherEntity = event.getMother();
			Entity fatherEntity = event.getFather();
			Entity childEntity = event.getEntity();

			FarmAnimal mother = OwnershipManager.getAnimal(motherEntity.getUniqueId());
	        FarmAnimal father = OwnershipManager.getAnimal(fatherEntity.getUniqueId());

			boolean playerIsOwner = playerOwnsParents(player, fatherEntity, motherEntity);

			if (MountUtils.isNeutered(motherEntity) || MountUtils.isNeutered(fatherEntity)) {
				event.setCancelled(true);
				if (motherEntity instanceof AbstractHorse && fatherEntity instanceof AbstractHorse) {
					player.sendMessage(Messages.neuteredHorse);
					((AbstractHorse) fatherEntity).setLoveModeTicks(0);
					((AbstractHorse) motherEntity).setLoveModeTicks(0);
					//event.setExperience(0);
					return;
				}
			}

			if (playerIsOwner) {
				if (MountUtils.isFullStatMount(childEntity)) {
					Bukkit.getScheduler().runTaskLater(BreedingBuddies.getInstance(), () -> {
						MountUtils.applyInheritedStats(
								(LivingEntity) childEntity,
								(LivingEntity) motherEntity,
								(LivingEntity) fatherEntity
						);
						((AbstractHorse) childEntity).setTamed(true);
					}, 1L);

					FarmAnimal child = new FarmAnimal(childEntity.getUniqueId(), childEntity);
					UnownedAnimalsManager.addUnownedAnimal(child);
				} else {
					specialBreed(mother, father, childEntity);
				}
			} else if (parentsHaveOwner(fatherEntity, motherEntity) && !playerIsOwner) {
				event.setCancelled(true);
			}
		}
	}
	
	public void specialBreed(FarmAnimal mother, FarmAnimal father, Entity childEntity) {
	    int friendshipAverage = (mother.getFriendshipPoints() + father.getFriendshipPoints()) / 2;
	    int geneticAverage = (mother.getGeneticPoints() + father.getGeneticPoints()) / 2;

	    int geneticVariance = calculateGeneticVariance(geneticAverage);
	    int friendshipInfluence = calculateFriendshipInfluence(friendshipAverage);

	    Random random = new Random();
	    int childGeneticPoints = geneticAverage + random.nextInt(geneticVariance) + friendshipInfluence;
	    childGeneticPoints = Math.min(childGeneticPoints, Numbers.maxFriendshipAndGenetics); // asegurarse de no exceder el máximo

	    FarmAnimal child = new FarmAnimal(childEntity.getUniqueId(), childGeneticPoints, childEntity);
		UnownedAnimalsManager.addUnownedAnimal(child);
	}

	private int calculateGeneticVariance(int geneticAverage) {
		double effectiveDivider = 10.0 * Numbers.geneticDivisorMultiplierToRetardProgression;
		return Math.max(100, (int) (Numbers.geneticVarianceMultiplier * (1000 - geneticAverage / effectiveDivider)));
	}

	private int calculateFriendshipInfluence(int friendshipAverage) {
	    // La influencia de la amistad puede mejorar la genética del hijo
		// 5% de la amistad media se agrega a la genética
		return (int) (friendshipAverage * 0.05 * Numbers.friendshipInfluenceMultiplier); 
	}
	
	public boolean parentsHaveOwner(Entity father, Entity mother) {
		return OwnershipManager.hasOwner(father.getUniqueId())
				&& OwnershipManager.hasOwner(mother.getUniqueId());
	}
	
	public boolean playerOwnsParents(Player player, Entity father, Entity mother) {
		return OwnershipManager.isOwner(player.getUniqueId(), father.getUniqueId())
				&& OwnershipManager.isOwner(player.getUniqueId(), mother.getUniqueId());
	}
}
