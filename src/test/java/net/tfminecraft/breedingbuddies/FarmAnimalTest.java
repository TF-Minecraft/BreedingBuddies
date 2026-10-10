package net.tfminecraft.breedingbuddies;

import net.tfminecraft.breedingbuddies.configurables.Numbers;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Horse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FarmAnimalTest extends PluginTestBase {

    private static Date ago(Duration duration) {
        return new Date(System.currentTimeMillis() - duration.toMillis());
    }

    private Horse maxStatHorse() {
        Horse horse = spawn(Horse.class);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30);
        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3375);
        horse.setJumpStrength(1.0);
        return horse;
    }

    @Test
    void tamedFarmAnimalStartsHappyWithItsOwnerAndRandomGenetics() {
        Cow cow = spawn(Cow.class);
        UUID owner = UUID.randomUUID();

        FarmAnimal animal = new FarmAnimal(cow.getUniqueId(), "Daisy", owner, cow);

        assertEquals(cow.getUniqueId(), animal.getUuid());
        assertEquals("Daisy", animal.getName());
        assertEquals(Set.of(owner), animal.getOwnersUuids());
        assertEquals(AnimalStates.HAPPY, animal.getState());
        assertTrue(animal.getGeneticPoints() >= 0 && animal.getGeneticPoints() < Numbers.initialGeneticMax);
        assertTrue(animal.isStableNeeded());
        assertEquals(0, animal.getFriendshipPoints());
    }

    @Test
    void mountsDoNotNeedAStableAndTakeGeneticsFromTheirStats() {
        Horse horse = maxStatHorse();
        UUID owner = UUID.randomUUID();

        FarmAnimal tamed = new FarmAnimal(horse.getUniqueId(), "Storm", owner, horse);
        FarmAnimal inherited = new FarmAnimal(horse.getUniqueId(), "Storm", owner, horse, 12);
        FarmAnimal spawned = new FarmAnimal(horse.getUniqueId(), "HORSE", owner, horse, 12, 300);
        FarmAnimal bred = new FarmAnimal(horse.getUniqueId(), 12, horse);
        FarmAnimal born = new FarmAnimal(horse.getUniqueId(), horse);

        for (FarmAnimal animal : new FarmAnimal[] {tamed, inherited, spawned, bred, born}) {
            assertFalse(animal.isStableNeeded());
            assertEquals(Numbers.maxFriendshipAndGenetics, animal.getGeneticPoints());
        }
    }

    @Test
    void inheritedGeneticsAreKeptForNonMounts() {
        Cow cow = spawn(Cow.class);
        UUID owner = UUID.randomUUID();

        FarmAnimal animal = new FarmAnimal(cow.getUniqueId(), "Daisy", owner, cow, 1234);

        assertEquals(1234, animal.getGeneticPoints());
        assertEquals(Set.of(owner), animal.getOwnersUuids());
        assertEquals(AnimalStates.HAPPY, animal.getState());
        assertTrue(animal.isStableNeeded());
    }

    @Test
    void unownedAnimalsAreNamelessUntilTamed() {
        Cow cow = spawn(Cow.class);

        FarmAnimal spawned = new FarmAnimal(cow.getUniqueId(), "COW", UUID.randomUUID(), cow, 70, 300);
        FarmAnimal bred = new FarmAnimal(cow.getUniqueId(), 55, cow);
        FarmAnimal born = new FarmAnimal(cow.getUniqueId(), cow);

        assertEquals("???", spawned.getName());
        assertEquals(AnimalStates.UNOWNED, spawned.getState());
        assertEquals(300, spawned.getFriendshipPoints());
        assertEquals(70, spawned.getGeneticPoints());
        assertTrue(spawned.getOwnersUuids().isEmpty());
        assertEquals("???", bred.getName());
        assertEquals(AnimalStates.UNOWNED, bred.getState());
        assertEquals(55, bred.getGeneticPoints());
        assertEquals("???", born.getName());
        assertEquals(0, born.getGeneticPoints());
        assertTrue(born.isStableNeeded());
    }

    @Test
    void loadedAnimalsUseTheStoredOwnerSet() {
        Set<UUID> owners = new HashSet<>(Set.of(UUID.randomUUID()));
        FarmAnimal animal = new FarmAnimal(UUID.randomUUID(), "Bessie", owners);

        assertSame(owners, animal.getOwnersUuids());
        Set<UUID> replaced = new HashSet<>();
        animal.setOwnersUuids(replaced);
        assertSame(replaced, animal.getOwnersUuids());
        animal.setName("Clover");
        assertEquals("Clover", animal.getName());
    }

    @Test
    void friendshipGrowsAndNeverDropsBelowZero() {
        FarmAnimal animal = new FarmAnimal(UUID.randomUUID(), "Daisy", new HashSet<>());

        animal.increaseFriendship(150);
        animal.increaseFriendship(50);
        assertEquals(200, animal.getFriendshipPoints());

        animal.setFriendshipPoints(-5);
        assertEquals(0, animal.getFriendshipPoints());
    }

    @Test
    void wellKeptAnimalIsHappyAfterTheDayAndItsDailyCareResets() {
        FarmAnimal animal = new FarmAnimal(UUID.randomUUID(), "Daisy", new HashSet<>());
        animal.setFriendshipPoints(500);
        animal.setFed(true);
        animal.setCared(true);

        animal.changeDay(false, false, true, 100);

        assertEquals(AnimalStates.HAPPY, animal.getState());
        assertFalse(animal.getFed());
        assertFalse(animal.getCared());
        assertEquals(400, animal.getFriendshipPoints());
        assertTrue(animal.isSleptInStable());
        assertFalse(animal.isWaterNeeded());
        assertFalse(animal.isSpaceNeeded());
        assertNotNull(animal.getLastUpdated());
    }

    @ParameterizedTest
    @CsvSource({
            "true,false,true,true,true",
            "false,true,true,true,true",
            "false,false,false,true,true",
            "false,false,true,false,true",
            "false,false,true,true,false"
    })
    void anyMissingNeedMakesTheAnimalSad(boolean water, boolean space, boolean slept, boolean fed, boolean cared) {
        FarmAnimal animal = new FarmAnimal(UUID.randomUUID(), "Daisy", new HashSet<>());
        animal.setFed(fed);
        animal.setCared(cared);

        animal.changeDay(water, space, slept, 0);

        assertEquals(AnimalStates.SAD, animal.getState());
        assertEquals(water, animal.isWaterNeeded());
        assertEquals(space, animal.isSpaceNeeded());
        assertEquals(slept, animal.isSleptInStable());
    }

    @Test
    void aDayChangeWithinTwentyFourHoursOfTheLastIsIgnored() {
        FarmAnimal animal = new FarmAnimal(UUID.randomUUID(), "Daisy", new HashSet<>());
        animal.setState(AnimalStates.HAPPY);
        animal.setFriendshipPoints(300);
        animal.setFed(true);
        Date recent = ago(Duration.ofHours(23));
        animal.setLastUpdated(recent);

        animal.changeDay(true, true, false, 100);

        assertEquals(AnimalStates.HAPPY, animal.getState());
        assertEquals(300, animal.getFriendshipPoints());
        assertTrue(animal.getFed());
        assertSame(recent, animal.getLastUpdated());

        animal.setLastUpdated(ago(Duration.ofHours(25)));
        animal.changeDay(true, true, false, 100);
        assertEquals(AnimalStates.SAD, animal.getState());
        assertEquals(200, animal.getFriendshipPoints());
    }

    @Test
    void daysOutCountUpAndCanBeRestored() {
        FarmAnimal animal = new FarmAnimal(UUID.randomUUID(), "Daisy", new HashSet<>());
        animal.incrementDaysOut();
        animal.incrementDaysOut();
        assertEquals(2, animal.getDaysOut());
        animal.setDaysOut(7);
        assertEquals(7, animal.getDaysOut());
    }

    @Test
    void onlyHappyAnimalsGiveRewardsAndOnlyAfterTheCooldown() {
        FarmAnimal animal = new FarmAnimal(UUID.randomUUID(), "Daisy", new HashSet<>());
        animal.setState(AnimalStates.SAD);
        assertFalse(animal.canCollectReward());

        animal.setState(AnimalStates.HAPPY);
        assertTrue(animal.canCollectReward());

        animal.setLastRewardCollected(ago(Duration.ofHours(7)));
        assertFalse(animal.canCollectReward());

        animal.setLastRewardCollected(ago(Duration.ofHours(8).plusMinutes(1)));
        assertTrue(animal.canCollectReward());
    }

    @Test
    void timeUntilTheNextBundleIsShownAsHoursAndMinutes() {
        Cow cow = spawn(Cow.class);
        FarmAnimal animal = new FarmAnimal(cow.getUniqueId(), "Daisy", UUID.randomUUID(), cow);

        assertEquals("00:00", animal.timeForNextReward(cow));

        animal.setLastRewardCollected(ago(Duration.ofHours(2).plusMinutes(29).plusSeconds(30)));
        assertEquals("05:30", animal.timeForNextReward(cow));

        animal.setLastRewardCollected(ago(Duration.ofDays(2)));
        assertEquals("00:00", animal.timeForNextReward(cow));

        animal.setState(AnimalStates.SAD);
        assertEquals("No bundle", animal.timeForNextReward(cow));
    }

    @Test
    void speciesWithoutBundlesNeverShowABundleTimer() {
        Horse horse = spawn(Horse.class);
        FarmAnimal animal = new FarmAnimal(horse.getUniqueId(), "Storm", UUID.randomUUID(), horse);

        assertEquals(AnimalStates.HAPPY, animal.getState());
        assertEquals("No bundle", animal.timeForNextReward(horse));
    }
}
