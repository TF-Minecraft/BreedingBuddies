package net.tfminecraft.breedingbuddies;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.Set;

public class AnimalUtils {
    private final static Set<EntityType> farmAnimals = new HashSet<>();
    private static FileConfiguration bundlesConfig;
    
    public AnimalUtils(FileConfiguration bundlesConfig) {
    	AnimalUtils.bundlesConfig = bundlesConfig;
    	for (String key : bundlesConfig.getKeys(false)) {
            try {
                EntityType entityType = EntityType.valueOf(key.toUpperCase());
                farmAnimals.add(entityType);
            } catch (IllegalArgumentException e) {
            	Bukkit.getLogger().info("Invalid entity type in configuration: " + key);
            }
        }
	}

    public static boolean isFarmAnimal(Entity entity) {
        return farmAnimals.contains(entity.getType());
    }
}
