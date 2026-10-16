package net.tfminecraft.breedingbuddies.husbandry;

import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;


public final class HusbandryStateDisplay {

    /** TextDisplay passengers from Cooking's old affliction UI. */
    private static final NamespacedKey LEGACY_STATE_LABEL =
            new NamespacedKey(HusbandryKeys.COOKING, "state_label");

    private HusbandryStateDisplay() {}

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void sync(LivingEntity entity, HusbandryAnimal animal) {
        if (entity == null || !entity.isValid() || animal == null) {
            return;
        }
        removeLegacyTextDisplays(entity);
        String baseName = resolveBaseName(animal, entity);
        String suffix = afflictionSuffix(animal);
        if (suffix == null) {
            entity.setCustomName(baseName);
            entity.setCustomNameVisible(false);
            return;
        }
        entity.setCustomName(baseName + " " + suffix);
        entity.setCustomNameVisible(true);
    }

    public static void removeAll(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        removeLegacyTextDisplays(entity);
    }

    private static void removeLegacyTextDisplays(LivingEntity entity) {
        for (Entity passenger : List.copyOf(entity.getPassengers())) {
            if (passenger instanceof TextDisplay display && legacyLabelOf(display) != null) {
                display.remove();
            }
        }
    }

    private static String resolveBaseName(HusbandryAnimal animal, LivingEntity entity) {
        if (animal.name() != null && !animal.name().isBlank() && !"???".equals(animal.name().trim())) {
            return animal.name();
        }
        return HusbandryEntities.displayName(entity.getType());
    }

    private static String afflictionSuffix(HusbandryAnimal animal) {
        boolean hungry = animal.hungrySince() != null;
        boolean dirty = animal.dirtySince() != null;
        if (hungry && dirty) {
            return "(Hungry, Dirty)";
        }
        if (hungry) {
            return "(Hungry)";
        }
        if (dirty) {
            return "(Dirty)";
        }
        return null;
    }

    private static String legacyLabelOf(TextDisplay display) {
        return display.getPersistentDataContainer().get(LEGACY_STATE_LABEL, PersistentDataType.STRING);
    }
}
