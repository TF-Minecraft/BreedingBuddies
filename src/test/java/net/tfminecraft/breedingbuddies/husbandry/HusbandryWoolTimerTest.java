package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HusbandryWoolTimerTest {

    @Test
    void speciesOverrideBeatsDefault() {
        assertEquals(2400, HusbandryConfig.resolveWoolTimerSeconds(2400, 1200));
        assertEquals(1200, HusbandryConfig.resolveWoolTimerSeconds(0, 1200));
        assertEquals(1200, HusbandryConfig.resolveWoolTimerSeconds(-1, 1200));
    }

    @Test
    void goatWoolCooldownBlocksSlaughterWool() {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "GOAT", "Test");
        animal.setWoolReadyAt(5_000L);
        assertTrue(HusbandryDropRoller.woolBlocked(animal.type(), animal, 1_000L));
        assertEquals(0, HusbandryDropRoller.hideCount(animal, 1_000L));
    }

    @Test
    void sheepBonusOnCooldownLeavesTimer() {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "SHEEP", "Wool");
        animal.setWoolReadyAt(5_000L);
        HusbandryHarvest.ShearResult result = HusbandryHarvest.trySheepBonusShear(
                null, animal, shearableSpecies(), null, 1_000L);
        assertEquals(HusbandryHarvest.ShearResult.COOLDOWN, result);
        assertEquals(5_000L, animal.woolReadyAt());
    }

    @Test
    void sheepBonusWhenReadyStartsWoolTimer() {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "SHEEP", "Wool");
        animal.setWoolReadyAt(1_000L);
        HusbandryHarvest.ShearResult result = HusbandryHarvest.trySheepBonusShear(
                null, animal, shearableSpecies(), null, 2_000L);
        assertEquals(HusbandryHarvest.ShearResult.DONE, result);
        long wait = HusbandryConfig.woolTimerSeconds() * 1000L;
        assertEquals(2_000L + wait, animal.woolReadyAt());
    }

    @Test
    void goatShearOnCooldownIsGated() {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "GOAT", "Billy");
        animal.setWoolReadyAt(5_000L);
        HusbandryHarvest.ShearResult result = HusbandryHarvest.tryShear(
                null, null, animal, shearableSpecies(), null, 1_000L);
        assertEquals(HusbandryHarvest.ShearResult.COOLDOWN, result);
        assertEquals(5_000L, animal.woolReadyAt());
    }

    private static HusbandrySpecies shearableSpecies() {
        HusbandryDropTable drops = new HusbandryDropTable(
                List.of(new HusbandryDropEntry("v.white_wool", 1, 100)),
                List.of(),
                List.of(),
                List.of(),
                true);
        return new HusbandrySpecies(null, false, "", HusbandryDropTable.empty(), drops, HusbandryDropTable.empty(), "", 0, 0, 0);
    }
}
