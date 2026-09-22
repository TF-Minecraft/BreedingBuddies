package net.tfminecraft.breedingbuddies;

import java.util.UUID;

public class UnownedAnimalsManager {

    public static void addUnownedAnimal(FarmAnimal animal) {
        PluginData.getUnownedAnimals().putIfAbsent(animal.getUuid(), animal);
    }

    public static void removeUnownedAnimal(UUID animalUuid) {
    	PluginData.getUnownedAnimals().remove(animalUuid);
    }

    public static FarmAnimal getAnimal(UUID animalUuid) {
        return PluginData.getUnownedAnimals().get(animalUuid);
    }

    public static boolean isUnowned(UUID animalUuid) {
        return PluginData.getUnownedAnimals().containsKey(animalUuid);
    }
}
