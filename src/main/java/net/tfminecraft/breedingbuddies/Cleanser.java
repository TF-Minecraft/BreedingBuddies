package net.tfminecraft.breedingbuddies;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.*;

public class Cleanser {
	public static void removeDisappearedAnimals() {
		Map<UUID, Set<FarmAnimal>> animals = PluginData.getPlayerAnimals();
		for (Set<FarmAnimal> animalList : animals.values()) {
			Iterator<FarmAnimal> it = animalList.iterator();
			while (it.hasNext()) {
				FarmAnimal animal = it.next();
				Entity entity = Bukkit.getEntity(animal.getUuid());
				if (entity == null) {
					it.remove();
				}
			}
		}
		Map<UUID, FarmAnimal> unownedAnimals = PluginData.getUnownedAnimals();
		Iterator<FarmAnimal> it = unownedAnimals.values().iterator();
		while (it.hasNext()) {
			FarmAnimal animal = it.next();
			Entity entity = Bukkit.getEntity(animal.getUuid());
			if (entity == null) {
				it.remove();
			}
		}
	}
}
