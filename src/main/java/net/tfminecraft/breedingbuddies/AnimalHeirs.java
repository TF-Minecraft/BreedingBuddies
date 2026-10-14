package net.tfminecraft.breedingbuddies;
import net.tfminecraft.tlibs.character.Characters;
import java.io.IOException;
/** Linked only after the heir API capability probe. */
final class AnimalHeirs {
    private AnimalHeirs() {}
    static void open(AnimalOwnershipStore store) throws IOException { store.open(Characters.legacyHeirsReady(), Characters::legacyHeir); }
}
