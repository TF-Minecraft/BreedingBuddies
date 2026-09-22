package net.tfminecraft.breedingbuddies;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class MountStats {
    public final double minHealth, maxHealth, minSpeed, maxSpeed, minJump, maxJump;
    public static final double defMinHealth = 15, defMaxHealth = 30;
    public static final double defMinSpeed = 0.1125, defMaxSpeed = 0.23;
    public static final double defMinJump = 0.4, defMaxJump = 0.6;    private static FileConfiguration bundlesConfig;
    private static FileConfiguration numbersConfig;

    public MountStats(double minHealth, double maxHealth,
                      double minSpeed, double maxSpeed,
                      double minJump, double maxJump) {
        this.minHealth = minHealth;
        this.maxHealth = maxHealth;
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.minJump = minJump;
        this.maxJump = maxJump;
    }

    public static MountStats getMountStats(String animalKey) {
        // Buscar el nombre real ignorando mayúsculas
        String realKey = bundlesConfig.getKeys(false).stream()
                .filter(key -> key.equalsIgnoreCase(animalKey))
                .findFirst()
                .orElse(null);

        // Buscar la sección de stats personalizada (si existe)
        ConfigurationSection mountSection = (realKey != null)
                ? bundlesConfig.getConfigurationSection(realKey + ".mountStats")
                : null;

        // Sección de valores por defecto (si existe)
        ConfigurationSection defaults = numbersConfig.getConfigurationSection("defaultMountStats");

        // Cargar los valores desde mountSection > defaults > hardcoded
        return new MountStats(
                getStat("minHealth", mountSection, defaults, defMinHealth),
                getStat("maxHealth", mountSection, defaults, defMaxHealth),
                getStat("minSpeed",  mountSection, defaults, defMinSpeed),
                getStat("maxSpeed",  mountSection, defaults, defMaxSpeed),
                getStat("minJump",   mountSection, defaults, defMinJump),
                getStat("maxJump",   mountSection, defaults, defMaxJump)
        );
    }

    private static double getStat(String key, ConfigurationSection primary, ConfigurationSection fallback, double hardcodedDefault) {
        if (primary != null && primary.contains(key)) return primary.getDouble(key);
        if (fallback != null && fallback.contains(key)) return fallback.getDouble(key);
        return hardcodedDefault;
    }

    public static void setConfigs(FileConfiguration bundles, FileConfiguration numbers) {
        bundlesConfig = bundles;
        numbersConfig = numbers;
    }

    public double getMinHealth() {
        return minHealth;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getMinSpeed() {
        return minSpeed;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getMinJump() {
        return minJump;
    }

    public double getMaxJump() {
        return maxJump;
    }
}
