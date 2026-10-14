package net.tfminecraft.breedingbuddies.husbandry;

import org.bukkit.NamespacedKey;

import net.tfminecraft.breedingbuddies.BreedingBuddies;

public final class HusbandryKeys {

    public static final NamespacedKey MANAGED =
            new NamespacedKey(BreedingBuddies.plugin, "managed");

    public static final NamespacedKey TAME_NAME =
            new NamespacedKey(BreedingBuddies.plugin, "tame_name");

    public static final NamespacedKey LINKED_ANIMAL =
            new NamespacedKey(BreedingBuddies.plugin, "linked_animal");

    private HusbandryKeys() {}
}
