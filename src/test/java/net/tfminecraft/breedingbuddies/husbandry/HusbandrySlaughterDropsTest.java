package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HusbandrySlaughterDropsTest {

    @Test
    void immatureSlaughterLeavesVanilla() {
        assertFalse(HusbandrySlaughterDrops.shouldReplaceVanilla(true, false));
        assertFalse(HusbandrySlaughterDrops.shouldAddRoast(true, false));
    }

    @Test
    void matureSlaughterClearsVanillaAndAddsRoast() {
        assertTrue(HusbandrySlaughterDrops.shouldReplaceVanilla(true, true));
        assertTrue(HusbandrySlaughterDrops.shouldAddRoast(true, true));
    }

    @Test
    void pigAlwaysGetsRoastWhenMature() {
        assertTrue(HusbandrySlaughterDrops.shouldAddRoast(true, true));
    }

    @Test
    void noSlaughterLeavesVanilla() {
        assertFalse(HusbandrySlaughterDrops.shouldReplaceVanilla(false, true));
        assertFalse(HusbandrySlaughterDrops.shouldAddRoast(false, true));
    }
}
