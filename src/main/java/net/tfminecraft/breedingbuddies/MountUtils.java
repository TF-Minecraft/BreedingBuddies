package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.configurables.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

public class MountUtils {
    /**
     * Comprueba si una entidad es una montura con stats (vida, salto, velocidad)
     */
    public static boolean isFullStatMount(Entity entity) {
        return entity instanceof AbstractHorse;
    }

    /**
     * Establece la vida máxima y actual
     */
    public static void setMaxHealth(LivingEntity entity, double health) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(health);
            entity.setHealth(Math.min(health, entity.getHealth())); // ajusta si la salud actual es mayor que el máximo
        }
    }

    /**
     * Establece la velocidad de movimiento
     */
    public static void setSpeed(LivingEntity entity, double speed) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.setBaseValue(speed);
        }
    }

    /**
     * Establece la fuerza de salto (solo para AbstractHorse y Camel)
     */
    public static void setJumpStrength(Entity entity, double jumpStrength) {
        if (entity instanceof AbstractHorse) {
            ((AbstractHorse) entity).setJumpStrength(jumpStrength);
        }
    }

    /**
     * Obtiene la fuerza de salto (si es aplicable)
     */
    public static double getJumpStrength(Entity entity) {
        if (entity instanceof AbstractHorse) {
            return ((AbstractHorse) entity).getJumpStrength();
        }
        return -1;
    }

    /**
     * Obtiene la velocidad base
     */
    public static double getSpeed(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        return attr != null ? attr.getBaseValue() : -1;
    }

    /**
     * Obtiene la vida máxima
     */
    public static double getMaxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getBaseValue() : -1;
    }

    /**
     * Convierte jumpStrength (0.4 a 1.0) a altura en bloques (Java Edition).
     */
    public static double jumpStrengthToBlockHeight(double strength) {
        return -0.1817584952 * Math.pow(strength, 3) + 3.689713992 * Math.pow(strength, 2) + 2.128599134 * strength - 0.343930367;
    }

    /**
     * Convierte altura en bloques a jumpStrength (0.4 a 1.0).
     */
    public static double blockHeightToJumpStrength(double height) {
        double a = -7.0;
        double b = 12.5;
        double c = -(2.0 + height);

        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return -1;

        double sqrt = Math.sqrt(discriminant);
        double s1 = (-b + sqrt) / (2 * a);
        double s2 = (-b - sqrt) / (2 * a);

        double result = Math.max(s1, s2);
        return Math.max(0.4, Math.min(result, 1.0));
    }

    /**
     * Convierte velocidad interna a bloques por segundo.
     */
    public static double speedToBlocksPerSecond(double internalSpeed) {
        return internalSpeed * 42.16;
    }

    /**
     * Convierte bloques por segundo a velocidad interna.
     */
    public static double blocksPerSecondToSpeed(double blocksPerSecond) {
        return blocksPerSecond / 42.16;
    }

    /**
     * Convierte los puntos de vida a corazones (enteros).
     * Por ejemplo, 30 puntos de vida => 15 corazones.
     */
    public static int healthPointsToHearts(double healthPoints) {
        return (int) (healthPoints / 2);
    }

    /**
     * Convierte corazones a puntos de vida.
     * Por ejemplo, 15 corazones => 30 puntos de vida.
     */
    public static double heartsToHealthPoints(int hearts) {
        return hearts * 2.0;
    }

    // Herencia con aleatoriedad controlada
    public static double breedAttribute(double parent1, double parent2, double min, double max) {
        double normP1 = (parent1 - min) / (max - min);
        double normP2 = (parent2 - min) / (max - min);

        double minVal = Math.min(normP1, normP2);
        double maxVal = Math.max(normP1, normP2);

        double offset = Math.pow(maxVal - minVal, 3) * 3.5;
        double adjMax = maxVal + ((1 - maxVal - offset) / 4.0);
        double adjMin = minVal - ((1 - minVal) / 5.5);

        double result = Math.random() * (adjMax - adjMin) + adjMin;
        result = Math.min(Math.max(result, 0), 1);

        return result * (max - min) + min;
    }

    public static void applyInheritedStats(LivingEntity child, LivingEntity parent1, LivingEntity parent2) {
        //1
        MountStats mountStats = MountStats.getMountStats(child.getName());
        double minHealth = mountStats.getMinHealth();
        double maxHealth = mountStats.getMaxHealth();

        double minSpeed = mountStats.getMinSpeed();
        double maxSpeed = mountStats.getMaxSpeed();

        double minJump = mountStats.getMinJump();
        double maxJump = mountStats.getMaxJump();

        if (!isFullStatMount(child) || !isFullStatMount(parent1) || !isFullStatMount(parent2)) return;

        double speed1 = Math.min(getSpeed(parent1), maxSpeed);
        double speed2 = Math.min(getSpeed(parent2), maxSpeed);
        double speed = breedAttribute(speed1, speed2, minSpeed, maxSpeed);
        setSpeed(child, speed);

        double health1 = Math.min(getMaxHealth(parent1), maxHealth);
        double health2 = Math.min(getMaxHealth(parent2), maxHealth);

        double health = breedAttribute(health1, health2, minHealth, maxHealth);
        setMaxHealth(child, health);

        double jump1 = Math.min(getJumpStrength(parent1), maxJump);
        double jump2 = Math.min(getJumpStrength(parent2), maxJump);
        double jump = (jump1 >= 0 && jump2 >= 0) ? breedAttribute(jump1, jump2, minJump, maxJump) : -1;

        if (jump >= 0) {
            setJumpStrength(child, jump);
        }
    }

    public static int calculateGeneticsFromStats(double health, double speed, double jump, String entityName) {
        MountStats mountStats = MountStats.getMountStats(entityName);

        double minHealth = mountStats.getMinHealth();
        double maxHealth = mountStats.getMaxHealth();

        double minSpeed = mountStats.getMinSpeed();
        double maxSpeed = mountStats.getMaxSpeed();

        double minJump = mountStats.getMinJump();
        double maxJump = mountStats.getMaxJump();

        double normHealth = (health - minHealth) / (maxHealth - minHealth);
        double normSpeed = (speed - minSpeed) / (maxSpeed - minSpeed);
        double normJump = (jump - minJump) / (maxJump - minJump);

        // Asegurar que estén entre 0 y 1
        normHealth = Math.min(1, Math.max(0, normHealth));
        normSpeed = Math.min(1, Math.max(0, normSpeed));
        normJump = Math.min(1, Math.max(0, normJump));

        double average = (normHealth + normSpeed + normJump) / 3.0;
        return (int) Math.round(average * Numbers.maxFriendshipAndGenetics);
    }

    public static int getGenetics(Entity entity) {
        LivingEntity livingEntity = (LivingEntity) entity;
        double health = MountUtils.getMaxHealth(livingEntity);
        double speed = MountUtils.getSpeed(livingEntity);
        double jump = MountUtils.getJumpStrength(livingEntity);

        return MountUtils.calculateGeneticsFromStats(health, speed, jump, entity.getName());
    }

    public static void setNeuteredFlag(Entity entity, boolean isNeutered) {
        NamespacedKey key = new NamespacedKey(BreedingBuddies.getInstance(), "is_neutered");
        entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (isNeutered ? 1 : 0));
    }

    public static boolean isNeutered(Entity entity) {
        NamespacedKey key = new NamespacedKey(BreedingBuddies.getInstance(), "is_neutered");
        Byte value = entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return (value != null && value == 1) || entity.getScoreboardTags().contains("ho.isNeutered");
    }
}

