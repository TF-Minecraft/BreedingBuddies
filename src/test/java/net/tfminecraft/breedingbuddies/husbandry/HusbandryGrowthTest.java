package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class HusbandryGrowthTest {

    @Test
    void immatureRecordKeepsTheBabyModel() {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "COW", "Calf");
        long now = 1_000_000L;
        animal.setMatureAt(now + 60_000L);
        assertEquals(HusbandryGrowth.ModelAge.BABY, HusbandryGrowth.modelAge(animal, now));
    }

    @Test
    void dueAndStoredAdultsUseTheAdultModel() {
        long now = 5_000L;
        HusbandryAnimal due = new HusbandryAnimal(UUID.randomUUID(), "CHICKEN", "Chick");
        due.setMatureAt(now);
        assertEquals(HusbandryGrowth.ModelAge.ADULT, HusbandryGrowth.modelAge(due, now));

        HusbandryAnimal adult = new HusbandryAnimal(UUID.randomUUID(), "COW", "Cow");
        adult.setMatureAt(null);
        assertEquals(HusbandryGrowth.ModelAge.ADULT, HusbandryGrowth.modelAge(adult, now));
        assertEquals(HusbandryGrowth.ModelAge.ADULT, HusbandryGrowth.modelAge(null, now));
    }
}
