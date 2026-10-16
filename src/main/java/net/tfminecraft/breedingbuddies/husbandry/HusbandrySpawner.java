package net.tfminecraft.breedingbuddies.husbandry;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class HusbandrySpawner {

    private HusbandrySpawner() {}

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static LivingEntity spawn(Player player, EntityType type, int genetics, int care) {
        if (player == null || type == null || type.getEntityClass() == null
                || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            return null;
        }
        Location at = player.getLocation();
        LivingEntity living = (LivingEntity) player.getWorld().spawnEntity(at, type);

        int clampedGenetics = Math.max(0, Math.min(HusbandryConfig.maxGenetics(), genetics));
        int clampedCare = Math.max(0, Math.min(HusbandryConfig.careMax(), care));
        HusbandryAnimal animal = createRecord(living, clampedGenetics, clampedCare);
        if (animal == null) {
            living.remove();
            return null;
        }
        living.setCustomName(animal.name());
        living.setCustomNameVisible(false);
        return living;
    }

    public static HusbandryAnimal createWildRecord(LivingEntity living) {
        int max = HusbandryConfig.initialGeneticMax();
        int genetics = max <= 0 ? 0 : ThreadLocalRandom.current().nextInt(max + 1);
        return create(living, genetics, 0, true);
    }

    /** Staff-chosen genetics; a mount gets it for all three stats. */
    public static HusbandryAnimal createRecord(LivingEntity living, int genetics, int care) {
        return create(living, genetics, care, false);
    }

    private static HusbandryAnimal create(LivingEntity living, int genetics, int care, boolean wild) {
        if (living == null) {
            return null;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return null;
        }
        int clampedGenetics = Math.max(0, Math.min(HusbandryConfig.maxGenetics(), genetics));
        int clampedCare = Math.max(0, Math.min(HusbandryConfig.careMax(), care));
        String name = HusbandryEntities.displayName(living.getType());
        HusbandryEntities.applyPersistFlags(living);
        HusbandryEntities.stampManaged(living);

        long now = System.currentTimeMillis();
        HusbandryAnimal animal = new HusbandryAnimal(living.getUniqueId(), living.getType().name(), name);
        animal.setState(HusbandryAnimalState.UNTAMED);
        animal.setGenetics(clampedGenetics);
        if (HusbandryMounts.isMount(living)) {
            if (wild) {
                HusbandryMountGenes.rollWild(animal, ThreadLocalRandom.current());
            } else {
                HusbandryMountGenes.set(animal, clampedGenetics, clampedGenetics, clampedGenetics);
            }
        }
        animal.setCare(clampedCare);
        animal.setStatsRevision(HusbandryConfig.statsRevision());
        animal.setLastProcessedAt(now);
        animal.setLoadedVisitStart(now);
        animal.setUnloadedAt(null);
        HusbandryHarvest.prepareNewAnimal(animal, living.getType());
        repository.upsertAnimal(animal);
        HusbandryEntities.putLoaded(animal);
        HusbandryMounts.applyStats(living, animal);
        return animal;
    }
}
