package net.tfminecraft.breedingbuddies;

import net.tfminecraft.tlibs.character.CharacterRef;
import java.util.*;

public class OwnershipManager {
    private OwnershipManager() {}
    public static void registerOwnership(UUID account, FarmAnimal animal) {
        CharacterRef ref = AnimalIdentity.session(account).orElseThrow(() -> new IllegalStateException("Current character required")).ref();
        if (PluginData.isHeld(animal.getUuid()) || hasHeldSlots(animal.getUuid())) throw new IllegalStateException("Animal ownership requires review");
        CharacterRef existing = animal.getOwnerRefs().get(ref.characterId());
        if (existing != null && !existing.equals(ref)) throw new IllegalStateException("Character owner account conflicts");
        if (animal.getOwnerRefs().isEmpty()) animal.getOwnersUuids().clear();
        animal.getOwnerRefs().put(ref.characterId(), ref); animal.getOwnersUuids().add(ref.characterId());
        PluginData.getPlayerAnimals().computeIfAbsent(ref.characterId(), ignored -> new HashSet<>()).add(animal);
        PluginData.getUnownedAnimals().remove(animal.getUuid());
        PluginData.saveAnimals();
    }
    public static void removeOwnership(UUID account, FarmAnimal animal) {
        AnimalIdentity.session(account).ifPresent(session -> {
            if (!PluginData.isHeld(animal.getUuid()) && session.ref().equals(animal.getOwnerRefs().get(session.ref().characterId()))) {
                UUID character = session.ref().characterId(); animal.getOwnerRefs().remove(character); animal.getOwnersUuids().remove(character);
                PluginData.getPlayerAnimals().getOrDefault(character, new HashSet<>()).remove(animal);
                if (animal.getOwnerRefs().isEmpty()) PluginData.getUnownedAnimals().put(animal.getUuid(), animal);
                PluginData.saveAnimals();
            }
        });
    }
    public static void removeAnimal(UUID animal) {
        if (PluginData.isHeld(animal)) return;
        PluginData.getPlayerAnimals().values().forEach(animals -> animals.removeIf(value -> { if (!value.getUuid().equals(animal)) return false; value.getOwnersUuids().clear(); value.getOwnerRefs().clear(); return true; }));
        try { if (!PluginData.getUnownedAnimals().containsKey(animal)) PluginData.ownership().forget(animal); PluginData.saveAnimals(); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    public static Set<FarmAnimal> getAnimals(UUID account) {
        return AnimalIdentity.session(account).map(s -> PluginData.getPlayerAnimals().getOrDefault(s.ref().characterId(), new HashSet<>())).orElse(Set.of());
    }
    public static boolean isOwner(UUID account, UUID animal) {
        if (PluginData.isHeld(animal)) return false;
        return AnimalIdentity.session(account).map(s -> getAnimals(account).stream().anyMatch(a -> a.getUuid().equals(animal) && s.ref().equals(a.getOwnerRefs().get(s.ref().characterId())))).orElse(false);
    }
    public static boolean hasOwner(UUID animal) { return PluginData.ownership() == null || PluginData.ownership().hasOwner(animal); }
    public static boolean hasHeldSlots(UUID animal) {
        if (PluginData.isHeld(animal)) return true;
        var row = PluginData.ownership().rows.get(animal); return row != null && !((org.json.simple.JSONArray) row.get("heldOwners")).isEmpty();
    }
    public static FarmAnimal getAnimal(UUID animal, UUID account) { return isOwner(account, animal) ? getAnimal(animal) : null; }
    public static FarmAnimal getAnimal(UUID animal) {
        if (PluginData.isHeld(animal)) return null;
        return PluginData.getPlayerAnimals().values().stream().flatMap(Set::stream).filter(a -> a.getUuid().equals(animal)).findFirst().orElse(null);
    }
}
