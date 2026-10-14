package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class HusbandryGrowth {

    public enum ModelAge {
        BABY,
        ADULT
    }

    private HusbandryGrowth() {}

    public static boolean isMature(HusbandryAnimal animal, long nowMillis) {
        if (animal == null) {
            return true;
        }
        Long matureAt = animal.matureAt();
        return matureAt == null || nowMillis >= matureAt;
    }

    /** Baby model until husbandry maturity. A missing record is already grown. */
    public static ModelAge modelAge(HusbandryAnimal animal, long nowMillis) {
        return isMature(animal, nowMillis) ? ModelAge.ADULT : ModelAge.BABY;
    }

    public static long computeMatureAt(EntityType type, long bornAtMillis) {
        return bornAtMillis + HusbandryConfig.growUpSeconds(type) * 1000L;
    }

    public static void applyMaturity(LivingEntity entity, HusbandryAnimal animal, long nowMillis) {
        if (animal == null || entity == null) {
            return;
        }
        if (modelAge(animal, nowMillis) == ModelAge.BABY) {
            if (entity instanceof Breedable breedable) {
                holdBaby(breedable);
            }
            return;
        }
        if (animal.matureAt() != null) {
            animal.setMatureAt(null);
        }
        if (entity instanceof Breedable breedable) {
            releaseAdult(breedable);
            return;
        }
        if (entity instanceof Ageable ageable && !ageable.isAdult()) {
            ageable.setAdult();
        }
    }

    private static void holdBaby(Breedable breedable) {
        if (breedable.isAdult()) {
            breedable.setBaby();
        }
        if (!breedable.getAgeLock()) {
            breedable.setAgeLock(true);
        }
    }

    private static void releaseAdult(Breedable breedable) {
        if (breedable.getAgeLock()) {
            breedable.setAgeLock(false);
        }
        if (!breedable.isAdult()) {
            breedable.setAdult();
        }
    }
}
