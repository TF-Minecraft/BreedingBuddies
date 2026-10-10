package net.tfminecraft.breedingbuddies;

import java.util.*;

public class OwnershipManager {
	private OwnershipManager() {
	}

	public static void registerOwnership (UUID playerUuid, FarmAnimal animal) {
		Set<FarmAnimal> animals = PluginData.getPlayerAnimals().getOrDefault(playerUuid, new HashSet<>());
	    if (!animals.contains(animal)) {
	        animals.add(animal);
	        PluginData.getPlayerAnimals().put(playerUuid, animals);
	        animal.getOwnersUuids().add(playerUuid);
	    }
	}

	public static void removeOwnership (UUID playerUuid, FarmAnimal animal) {
		Set<FarmAnimal> animals = PluginData.getPlayerAnimals().get(playerUuid);
		if (animals != null) {
			animal.getOwnersUuids().remove(playerUuid);
			animals.remove(animal);
			PluginData.getPlayerAnimals().put(playerUuid, animals);
		}
	}

	public static void removeAnimal(UUID animalUuid) {
		PluginData.getPlayerAnimals().forEach((uuid, animals) -> {
			animals.removeIf(animal -> {
				if (animal.getUuid().equals(animalUuid)) {
					animal.getOwnersUuids().clear(); // limpia la lista de owners
					return true;
				}
				return false;
			});
		});
	}

	public static Set<FarmAnimal> getAnimals(UUID playerUuid) {
		return PluginData.getPlayerAnimals().getOrDefault(playerUuid, new HashSet<>());
    }
	
	public static boolean isOwner(UUID playerUuid, UUID animalUuid) {
        Set<FarmAnimal> animals = PluginData.getPlayerAnimals().get(playerUuid);
        if (animals != null) {
            for (FarmAnimal animal : animals) {
                if (animal.getUuid().equals(animalUuid)) {
                    return true;
                }
            }
        }
        return false;
    }
	
	public static boolean hasOwner(UUID animalUuid) {
        return PluginData.getPlayerAnimals().values().stream()
                .flatMap(Set::stream)
                .anyMatch(animal -> animal.getUuid().equals(animalUuid) && !animal.getOwnersUuids().isEmpty());
    }
	
	public static FarmAnimal getAnimal(UUID animalUuid, UUID playerUuid) {
		Set<FarmAnimal> animals = PluginData.getPlayerAnimals().get(playerUuid);

		if (animals != null) {
			for (FarmAnimal animal : animals) {
				if (animal.getUuid().equals(animalUuid)) {
					return animal;
				}
			}
		}
		return null;
	}
	
	public static FarmAnimal getAnimal(UUID animalUuid) {
		return PluginData.getPlayerAnimals().values().stream()
                .flatMap(Set::stream)
                .filter(animal -> animal.getUuid().equals(animalUuid))
                .findFirst()
                .orElse(null);
	}
}
