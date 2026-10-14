package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HusbandryMountEnrollTest {

    @Test
    void wildHorseWithoutRowIsWiped() {
        assertTrue(HusbandryMounts.shouldWipeUnowned(true, false, false, true));
    }

    @Test
    void enrolledUnownedHorseIsKept() {
        assertFalse(HusbandryMounts.shouldWipeUnowned(true, false, true, true));
    }

    @Test
    void ownedEntityIsKeptEvenIfNotConfiguredMount() {
        assertFalse(HusbandryMounts.shouldWipeUnowned(true, true, false, false));
    }

    @Test
    void unownedCowWithRowIsWiped() {
        assertTrue(HusbandryMounts.shouldWipeUnowned(true, false, true, false));
    }

    @Test
    void llamaWithRowIsWiped() {
        assertTrue(HusbandryMounts.shouldWipeUnowned(true, false, true, false));
    }

    @Test
    void enrollRowOnlyWhenConfiguredAndMissing() {
        assertTrue(HusbandryMounts.shouldCreateEnrollRow(true, false));
        assertFalse(HusbandryMounts.shouldCreateEnrollRow(true, true));
        assertFalse(HusbandryMounts.shouldCreateEnrollRow(false, false));
    }
}
