package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HusbandrySimulatorTest {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;

    @Test
    void happyLoadedTimeNeverGrantsTrust() {
        HusbandryAnimal animal = happyAnimal(40);
        long now = animal.lastProcessedAt();
        for (int i = 1; i <= 120; i++) {
            HusbandrySimulator.tickLoaded(animal, now + i * MINUTE, new Random(1));
        }
        assertEquals(40, animal.care());
        assertTrue(HusbandrySimulator.isHappy(animal));
    }

    @Test
    void happyUnloadedTimeNeverGrantsTrust() {
        HusbandryAnimal animal = happyAnimal(40);
        animal.setUnloadedAt(animal.lastProcessedAt());
        HusbandrySimulator.catchUp(animal, animal.lastProcessedAt() + 2 * HOUR, new Random(1));
        assertEquals(40, animal.care());
    }

    @Test
    void loadedTimeStillBringsAfflictionsWithoutChangingTrust() {
        HusbandryAnimal animal = happyAnimal(40);
        animal.setAfflictionAt(0.001);
        animal.setAfflictionElapsed(0);
        HusbandrySimulator.tickLoaded(animal, animal.lastProcessedAt() + 2 * HOUR, new Random(1));
        assertEquals(40, animal.care());
        assertFalse(HusbandrySimulator.isHappy(animal));
        assertTrue(animal.hungrySince() != null || animal.dirtySince() != null);
    }

    private static HusbandryAnimal happyAnimal(int care) {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "GOAT", "Test");
        animal.setCare(care);
        animal.setLastProcessedAt(1_000_000L);
        animal.setLoadedVisitStart(1_000_000L);
        animal.setAfflictionAt(24);
        animal.setAfflictionElapsed(0);
        return animal;
    }
}
