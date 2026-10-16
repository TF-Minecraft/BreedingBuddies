package net.tfminecraft.breedingbuddies.husbandry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.breedingbuddies.BreedingBuddies;

public final class HusbandryEntities {

    private static final Map<UUID, HusbandryAnimal> LOADED = new ConcurrentHashMap<>();

    private HusbandryEntities() {}

    public static Set<UUID> loadedIds() {
        return Collections.unmodifiableSet(LOADED.keySet());
    }

    public static List<HusbandryAnimal> snapshotLoaded() {
        return new ArrayList<>(LOADED.values());
    }

    public static Optional<HusbandryAnimal> getLoaded(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOADED.get(uuid));
    }

    public static void putLoaded(HusbandryAnimal animal) {
        if (animal == null || animal.uuid() == null) {
            return;
        }
        LOADED.put(animal.uuid(), animal);
    }

    public static void evict(UUID uuid) {
        if (uuid != null) {
            LOADED.remove(uuid);
        }
    }

    public static void untrack(UUID uuid) {
        evict(uuid);
    }

    public static void clearLoaded() {
        LOADED.clear();
    }

    /**
     * Cache-first lookup, then SQLite for unloaded or offline animals.
     * Loaded animals always return the canonical in-memory instance.
     */
    public static Optional<HusbandryAnimal> lookup(UUID uuid) {
        Optional<HusbandryAnimal> loaded = getLoaded(uuid);
        if (loaded.isPresent()) {
            return loaded;
        }
        HusbandryRepository repository = repository();
        if (repository == null || uuid == null) {
            return Optional.empty();
        }
        return repository.getAnimal(uuid);
    }

    public static boolean isManaged(Entity entity) {
        if (entity == null) {
            return false;
        }
        Byte value = HusbandryKeys.read(entity.getPersistentDataContainer(), HusbandryKeys.MANAGED, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    /** True for CompanionPets bodies, including before CompanionPets has enabled at startup. */
    public static boolean isCompanionPet(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(HusbandryKeys.COMPANION_PET, PersistentDataType.STRING);
    }

    public static void stampManaged(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(HusbandryKeys.MANAGED, PersistentDataType.BYTE, (byte) 1);
    }

    public static void applyPersistFlags(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
    }

    public static String displayName(org.bukkit.entity.EntityType type) {
        if (type == null) {
            return "Animal";
        }
        String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            out.append(word.substring(1));
        }
        return out.toString();
    }

    public static HusbandryRepository repository() {
        BreedingBuddies plugin = BreedingBuddies.plugin;
        if (plugin == null) {
            return null;
        }
        return plugin.getHusbandryRepository();
    }
}
