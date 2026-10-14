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
    void fiftyNineMinuteTicksDoNotGrantCare() {
        HusbandryAnimal animal = happyAnimal(0);
        long now = animal.lastProcessedAt();
        for (int i = 1; i <= 59; i++) {
            HusbandrySimulator.tickLoaded(animal, now + i * MINUTE, new Random(1));
        }
        assertEquals(0, animal.care());
        assertEquals(59 * 60L, animal.careUpRemainderSeconds());
    }

    @Test
    void sixtiethMinuteTickGrantsOneCare() {
        HusbandryAnimal animal = happyAnimal(0);
        long now = animal.lastProcessedAt();
        for (int i = 1; i <= 60; i++) {
            HusbandrySimulator.tickLoaded(animal, now + i * MINUTE, new Random(1));
        }
        assertEquals(1, animal.care());
        assertEquals(0, animal.careUpRemainderSeconds());
    }

    @Test
    void twoHourSliceGrantsTwoCare() {
        HusbandryAnimal animal = happyAnimal(0);
        long now = animal.lastProcessedAt();
        HusbandrySimulator.tickLoaded(animal, now + 2 * HOUR, new Random(1));
        assertEquals(2, animal.care());
        assertEquals(0, animal.careUpRemainderSeconds());
    }

    @Test
    void happyTimeGrantsCareBeforeAfflictionInSameSlice() {
        HusbandryAnimal animal = happyAnimal(0);
        animal.setAfflictionAt(0.001);
        animal.setAfflictionElapsed(0);
        long now = animal.lastProcessedAt();
        HusbandrySimulator.tickLoaded(animal, now + 2 * HOUR, new Random(1));
        assertEquals(2, animal.care());
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
