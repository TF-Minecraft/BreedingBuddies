package net.tfminecraft.breedingbuddies;

import java.util.*;

public class OwnershipManager {
	
	public static void registerOwnership (UUID playerUuid, FarmAnimal animal) {
		Set<FarmAnimal> animals = PluginData.getPlayerAnimals().getOrDefault(playerUuid, new HashSet<>());
	    if (!animals.contains(animal)) {
	        animals.add(animal);
	        PluginData.getPlayerAnimals().put(playerUuid, animals);
	        animal.getOwnersUuids().add(playerUuid);
	    }
	}

	public static void registerCoOwnership(UUID ownerUuid, UUID targetUuid, UUID animalUuid) {
		// 1. Buscar el animal en la lista del owner actual
		Set<FarmAnimal> ownerAnimals = PluginData.getPlayerAnimals().get(ownerUuid);
		if (ownerAnimals == null) return;

		FarmAnimal animal = ownerAnimals.stream()
				.filter(a -> a.getUuid().equals(animalUuid))
				.findFirst()
				.orElse(null);

		if (animal == null) return;

		// 2. Añadir el nuevo UUID a los owners del animal
		animal.getOwnersUuids().add(targetUuid);

		// 3. Añadir el animal a la lista del nuevo owner si no está ya
		Set<FarmAnimal> targetAnimals = PluginData.getPlayerAnimals()
				.computeIfAbsent(targetUuid, k -> new HashSet<>());

		if (!targetAnimals.contains(animal)) {
			targetAnimals.add(animal);
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
	
	public static ArrayList<UUID> getOwners(UUID animalUuid) {
        for (Set<FarmAnimal> animals : PluginData.getPlayerAnimals().values()) {
            for (FarmAnimal animal : animals) {
                if (animal.getUuid().equals(animalUuid)) {
                    return new ArrayList<>(animal.getOwnersUuids());
                }
            }
        }
        return new ArrayList<>();
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
