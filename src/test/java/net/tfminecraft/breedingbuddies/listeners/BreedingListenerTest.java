package net.tfminecraft.breedingbuddies.listeners;

import net.tfminecraft.breedingbuddies.AnimalStates;
import net.tfminecraft.breedingbuddies.FarmAnimal;
import net.tfminecraft.breedingbuddies.MountUtils;
import net.tfminecraft.breedingbuddies.PluginTestBase;
import net.tfminecraft.breedingbuddies.UnownedAnimalsManager;
import net.tfminecraft.breedingbuddies.configurables.Messages;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityBreedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class BreedingListenerTest extends PluginTestBase {
    private BreedingListener listener;
    private PlayerMock alice;

    @BeforeEach
    void createListener() {
        listener = new BreedingListener();
        alice = player();
    }

    private EntityBreedEvent breed(LivingEntity child, LivingEntity mother, LivingEntity father, LivingEntity breeder) {
        EntityBreedEvent event = new EntityBreedEvent(child, mother, father, breeder, null, 1);
        listener.onEntityBreed(event);
        return event;
    }

    private FarmAnimal ownedCow(PlayerMock owner, int genetics, int friendship) {
        FarmAnimal animal = own(owner, spawn(Cow.class));
        animal.setGeneticPoints(genetics);
        animal.setFriendshipPoints(friendship);
        return animal;
    }

    private Horse topHorse() {
        Horse horse = spawn(Horse.class);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30);
        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3375);
        horse.setJumpStrength(1.0);
        return horse;
    }

    private LivingEntity entityOf(FarmAnimal animal) {
        return (LivingEntity) server.getEntity(animal.getUuid());
    }

    @Test
    void calvesInheritTheirParentsGeneticsWithAFriendshipBonus() {
        FarmAnimal mother = ownedCow(alice, 1000, 4000);
        FarmAnimal father = ownedCow(alice, 3000, 6000);
        Cow calf = spawn(Cow.class);

        EntityBreedEvent event = breed(calf, entityOf(mother), entityOf(father), alice);

        assertFalse(event.isCancelled());
        FarmAnimal record = UnownedAnimalsManager.getAnimal(calf.getUniqueId());
        assertEquals(AnimalStates.UNOWNED, record.getState());
        // Average 2000, variance below 200 and a bonus of 5% x 0.4 of the average friendship.
        assertTrue(record.getGeneticPoints() >= 2100 && record.getGeneticPoints() < 2300, "genetics " + record.getGeneticPoints());
    }

    @Test
    void calvesNeverExceedTheGeneticsCap() {
        FarmAnimal mother = ownedCow(alice, 10_000, 10_000);
        FarmAnimal father = ownedCow(alice, 10_000, 10_000);
        Cow calf = spawn(Cow.class);

        breed(calf, entityOf(mother), entityOf(father), alice);

        assertEquals(10_000, UnownedAnimalsManager.getAnimal(calf.getUniqueId()).getGeneticPoints());
    }

    @Test
    void foalsInheritMountStatsOnTheNextTickAndAreTamed() {
        Horse mother = topHorse();
        Horse father = topHorse();
        own(alice, mother);
        own(alice, father);
        Horse foal = spawn(Horse.class);
        foal.getAttribute(Attribute.MAX_HEALTH).setBaseValue(15);

        breed(foal, mother, father, alice);

        assertEquals(AnimalStates.UNOWNED, UnownedAnimalsManager.getAnimal(foal.getUniqueId()).getState());
        assertFalse(foal.isTamed());
        server.getScheduler().performOneTick();
        assertTrue(foal.isTamed());
        assertEquals(30, MountUtils.getMaxHealth(foal), 1e-9);
        assertEquals(0.3375, MountUtils.getSpeed(foal), 1e-9);
        assertEquals(1.0, foal.getJumpStrength(), 1e-9);
    }

    @Test
    void onlyTheOwnerMayBreedOwnedAnimals() {
        FarmAnimal mother = ownedCow(alice, 100, 100);
        FarmAnimal father = ownedCow(alice, 100, 100);
        Cow calf = spawn(Cow.class);

        EntityBreedEvent event = breed(calf, entityOf(mother), entityOf(father), player());

        assertTrue(event.isCancelled());
        assertFalse(UnownedAnimalsManager.isUnowned(calf.getUniqueId()));
    }

    @Test
    void wildOrPartlyOwnedParentsBreedAsInVanilla() {
        Cow calf = spawn(Cow.class);
        FarmAnimal owned = ownedCow(alice, 100, 100);

        EntityBreedEvent wild = breed(calf, spawn(Cow.class), spawn(Cow.class), alice);
        EntityBreedEvent mixed = breed(calf, entityOf(owned), spawn(Cow.class), player());

        assertFalse(wild.isCancelled());
        assertFalse(mixed.isCancelled());
        assertFalse(UnownedAnimalsManager.isUnowned(calf.getUniqueId()));
    }

    @Test
    void breedingWithoutAPlayerIsIgnored() {
        FarmAnimal mother = ownedCow(alice, 100, 100);
        FarmAnimal father = ownedCow(alice, 100, 100);
        Cow calf = spawn(Cow.class);

        EntityBreedEvent event = breed(calf, entityOf(mother), entityOf(father), null);

        assertFalse(event.isCancelled());
        assertFalse(UnownedAnimalsManager.isUnowned(calf.getUniqueId()));
    }

    @Test
    void neuteredHorsesCannotBreed() {
        Horse mother = topHorse();
        Horse father = topHorse();
        own(alice, mother);
        own(alice, father);
        MountUtils.setNeuteredFlag(father, true);
        mother.setLoveModeTicks(600);
        father.setLoveModeTicks(600);
        Horse foal = spawn(Horse.class);

        EntityBreedEvent event = breed(foal, mother, father, alice);

        assertTrue(event.isCancelled());
        assertEquals(Messages.neuteredHorse, alice.nextMessage());
        assertEquals(0, mother.getLoveModeTicks());
        assertEquals(0, father.getLoveModeTicks());
        assertFalse(UnownedAnimalsManager.isUnowned(foal.getUniqueId()));
    }

    @Test
    void aNeuteredFlagOnOtherAnimalsAlsoBlocksBreeding() {
        FarmAnimal mother = ownedCow(alice, 100, 100);
        FarmAnimal father = ownedCow(alice, 100, 100);
        MountUtils.setNeuteredFlag(entityOf(mother), true);

        EntityBreedEvent event = breed(spawn(Cow.class), entityOf(mother), entityOf(father), alice);

        assertTrue(event.isCancelled());
        assertNull(alice.nextMessage());
    }
}
