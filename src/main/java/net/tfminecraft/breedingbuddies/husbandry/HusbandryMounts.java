package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class HusbandryMounts {

    private HusbandryMounts() {}

    public static boolean isMount(Entity entity) {
        return entity instanceof AbstractHorse;
    }

    public static boolean hasConfiguredStats(Entity entity) {
        return entity != null && HusbandryConfig.mountStats(entity.getType()) != null;
    }

    public static boolean shouldWipeUnowned(
            boolean removeUnownedType, boolean hasOwner, boolean hasRow, boolean configuredMount) {
        return removeUnownedType && !hasOwner && !(hasRow && configuredMount);
    }

    public static boolean shouldCreateEnrollRow(boolean configuredMount, boolean hasRow) {
        return configuredMount && !hasRow;
    }

    public static HusbandryAnimal enrollIfNeeded(LivingEntity entity) {
        if (entity == null || !hasConfiguredStats(entity)) {
            return null;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return null;
        }
        Optional<HusbandryAnimal> loaded = HusbandryEntities.getLoaded(entity.getUniqueId());
        boolean hasRow = loaded.isPresent() || repository.exists(entity.getUniqueId());
        if (!shouldCreateEnrollRow(true, hasRow)) {
            return loaded.orElseGet(() -> repository.getAnimal(entity.getUniqueId()).orElse(null));
        }
        return HusbandrySpawner.createWildRecord(entity);
    }

    public static void setMaxHealth(LivingEntity entity, double health) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) {
            return;
        }
        attr.setBaseValue(health);
        entity.setHealth(Math.min(health, entity.getHealth()));
    }

    public static void setSpeed(LivingEntity entity, double speed) {
        AttributeInstance attr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        if (Math.abs(attr.getBaseValue() - speed) < 0.0000001) {
            return;
        }
        attr.setBaseValue(speed);
    }

    public static double mountFraction(
            int genetics,
            int care,
            int maxGenetics,
            int careMax,
            double minPct,
            double geneticsPct,
            double carePct) {
        double min = Math.max(0, minPct);
        double gShare = Math.max(0, geneticsPct);
        double cShare = Math.max(0, carePct);
        double g = maxGenetics <= 0 ? 0 : Math.max(0, Math.min(1, genetics / (double) maxGenetics));
        double c = careMax <= 0 ? 0 : Math.max(0, Math.min(1, care / (double) careMax));
        return min + gShare * g + cShare * c;
    }

    public static double scaledStat(double max, double min, double fraction) {
        return Math.max(min, max * fraction);
    }

    public static double speedFor(
            double maxSpeed,
            int genetics,
            int care,
            int maxGenetics,
            int careMax,
            double minPct,
            double geneticsPct,
            double carePct) {
        return maxSpeed * mountFraction(
                genetics, care, maxGenetics, careMax, minPct, geneticsPct, carePct);
    }

    public static double speedFor(HusbandryAnimal animal, EntityType type) {
        HusbandryMountStats stats = statsFor(animal, type);
        if (stats == null) {
            return -1;
        }
        return scaledStat(stats.maxSpeed(), stats.minSpeed(), fractionFor(animal));
    }

    public static double healthFor(HusbandryAnimal animal, EntityType type) {
        HusbandryMountStats stats = statsFor(animal, type);
        if (stats == null) {
            return -1;
        }
        return scaledStat(stats.maxHealth(), stats.minHealth(), fractionFor(animal));
    }

    public static double jumpFor(HusbandryAnimal animal, EntityType type) {
        HusbandryMountStats stats = statsFor(animal, type);
        if (stats == null) {
            return -1;
        }
        return scaledStat(stats.maxJump(), stats.minJump(), fractionFor(animal));
    }

    public static void applyStats(LivingEntity entity, HusbandryAnimal animal) {
        if (entity == null || animal == null || !isMount(entity)) {
            return;
        }
        EntityType type = entity.getType();
        double health = healthFor(animal, type);
        if (health > 0) {
            setMaxHealth(entity, health);
        }
        double speed = speedFor(animal, type);
        if (speed > 0) {
            setSpeed(entity, speed);
        }
        double jump = jumpFor(animal, type);
        if (jump >= 0) {
            setJump(entity, jump);
        }
    }

    public static void setJump(Entity entity, double jump) {
        if (entity instanceof AbstractHorse horse) {
            horse.setJumpStrength(jump);
        }
    }

    public static double maxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getBaseValue() : -1;
    }

    public static double speed(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        return attr != null ? attr.getBaseValue() : -1;
    }

    public static double jump(Entity entity) {
        if (entity instanceof AbstractHorse horse) {
            return horse.getJumpStrength();
        }
        return -1;
    }

    public static double healthHearts(LivingEntity entity) {
        return maxHealth(entity) / 2.0;
    }

    public static double speedBlocksPerSecond(LivingEntity entity) {
        return speed(entity) * 42.16;
    }

    public static double jumpBlockHeight(Entity entity) {
        double strength = jump(entity);
        if (strength < 0) {
            return -1;
        }
        return -0.1817584952 * Math.pow(strength, 3)
                + 3.689713992 * Math.pow(strength, 2)
                + 2.128599134 * strength
                - 0.343930367;
    }

    private static HusbandryMountStats statsFor(HusbandryAnimal animal, EntityType type) {
        if (animal == null || type == null) {
            return null;
        }
        return HusbandryConfig.mountStats(type);
    }

    private static double fractionFor(HusbandryAnimal animal) {
        return mountFraction(
                animal.genetics(),
                animal.care(),
                HusbandryConfig.maxGenetics(),
                HusbandryConfig.careMax(),
                HusbandryConfig.mountSpeedMinPct(),
                HusbandryConfig.mountSpeedGeneticsPct(),
                HusbandryConfig.mountSpeedCarePct());
    }
}
