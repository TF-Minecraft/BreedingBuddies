package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.breedingbuddies.BreedingBuddies;

public final class HusbandryKeys {

    public static final NamespacedKey MANAGED =
            new NamespacedKey(BreedingBuddies.plugin, "managed");

    public static final NamespacedKey TAME_NAME =
            new NamespacedKey(BreedingBuddies.plugin, "tame_name");

    public static final NamespacedKey LINKED_ANIMAL =
            new NamespacedKey(BreedingBuddies.plugin, "linked_animal");

    /** Cooking ran livestock before BreedingBuddies; its entities and tokens keep these keys. */
    static final String COOKING = "cooking";

    private HusbandryKeys() {}

    /** Reads a BreedingBuddies key, or the same key Cooking wrote when the value predates the takeover. */
    static <T> T read(PersistentDataContainer data, NamespacedKey key, PersistentDataType<?, T> type) {
        T value = data.get(key, type);
        return value != null ? value : data.get(new NamespacedKey(COOKING, key.getKey()), type);
    }
}
