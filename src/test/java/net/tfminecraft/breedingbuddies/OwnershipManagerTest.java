package net.tfminecraft.breedingbuddies;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OwnershipManagerTest extends PluginTestBase {
    private UUID alice, bob;
    @org.junit.jupiter.api.BeforeEach void actors() { alice = player().getUniqueId(); bob = player().getUniqueId(); }

    private static FarmAnimal animal() {
        FarmAnimal a = new FarmAnimal(UUID.randomUUID(), "Daisy", new HashSet<>()); a.setState(AnimalStates.HAPPY); return a;
    }

    @Test
    void registeringOwnershipLinksBothSides() {
        FarmAnimal daisy = animal();

        OwnershipManager.registerOwnership(alice, daisy);
        OwnershipManager.registerOwnership(alice, daisy);

        assertEquals(Set.of(daisy), OwnershipManager.getAnimals(alice));
        assertEquals(Set.of(alice), daisy.getOwnersUuids());
        assertTrue(OwnershipManager.isOwner(alice, daisy.getUuid()));
        assertFalse(OwnershipManager.isOwner(bob, daisy.getUuid()));
        assertFalse(OwnershipManager.isOwner(alice, UUID.randomUUID()));
        assertTrue(OwnershipManager.hasOwner(daisy.getUuid()));
    }

    @Test
    void animalsCanBeFoundForAnyOwnerOrAParticularOne() {
        FarmAnimal daisy = animal();
        FarmAnimal clover = animal();
        OwnershipManager.registerOwnership(alice, daisy);
        OwnershipManager.registerOwnership(alice, clover);
        OwnershipManager.registerOwnership(bob, daisy);

        assertSame(daisy, OwnershipManager.getAnimal(daisy.getUuid()));
        assertSame(daisy, OwnershipManager.getAnimal(daisy.getUuid(), bob));
        assertNull(OwnershipManager.getAnimal(clover.getUuid(), bob));
        assertNull(OwnershipManager.getAnimal(daisy.getUuid(), UUID.randomUUID()));
        assertNull(OwnershipManager.getAnimal(UUID.randomUUID()));
        assertTrue(OwnershipManager.getAnimals(UUID.randomUUID()).isEmpty());
    }

    @Test
    void anAnimalListedWithNoOwnersHasNoOwner() {
        FarmAnimal daisy = animal();
        OwnershipManager.registerOwnership(alice, daisy);
        OwnershipManager.removeOwnership(alice, daisy);

        assertFalse(OwnershipManager.hasOwner(daisy.getUuid()));
        assertFalse(OwnershipManager.hasOwner(UUID.randomUUID()));
    }

    @Test
    void givingUpOwnershipLeavesOtherCoOwnersInPlace() {
        FarmAnimal daisy = animal();
        OwnershipManager.registerOwnership(alice, daisy);
        OwnershipManager.registerOwnership(bob, daisy);

        OwnershipManager.removeOwnership(alice, daisy);
        OwnershipManager.removeOwnership(UUID.randomUUID(), daisy);

        assertFalse(OwnershipManager.isOwner(alice, daisy.getUuid()));
        assertTrue(OwnershipManager.isOwner(bob, daisy.getUuid()));
        assertEquals(Set.of(bob), daisy.getOwnersUuids());
    }

    @Test
    void removingAnAnimalDropsItFromEveryOwner() {
        FarmAnimal daisy = animal();
        FarmAnimal clover = animal();
        OwnershipManager.registerOwnership(alice, daisy);
        OwnershipManager.registerOwnership(bob, daisy);
        OwnershipManager.registerOwnership(bob, clover);

        OwnershipManager.removeAnimal(daisy.getUuid());

        assertTrue(OwnershipManager.getAnimals(alice).isEmpty());
        assertEquals(Set.of(clover), OwnershipManager.getAnimals(bob));
        assertTrue(daisy.getOwnersUuids().isEmpty());
        assertEquals(Set.of(bob), clover.getOwnersUuids());
    }

    @Test
    void unownedRegistryKeepsTheFirstRecordForAnAnimal() {
        FarmAnimal first = animal();
        FarmAnimal duplicate = new FarmAnimal(first.getUuid(), "Other", new HashSet<>());

        UnownedAnimalsManager.addUnownedAnimal(first);
        UnownedAnimalsManager.addUnownedAnimal(duplicate);

        assertTrue(UnownedAnimalsManager.isUnowned(first.getUuid()));
        assertSame(first, UnownedAnimalsManager.getAnimal(first.getUuid()));

        UnownedAnimalsManager.removeUnownedAnimal(first.getUuid());
        assertFalse(UnownedAnimalsManager.isUnowned(first.getUuid()));
        assertNull(UnownedAnimalsManager.getAnimal(first.getUuid()));
    }
}
