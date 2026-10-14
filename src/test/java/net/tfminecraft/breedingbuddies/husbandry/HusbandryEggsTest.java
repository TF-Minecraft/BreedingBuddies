package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class HusbandryEggsTest {

    @Test
    void everyChickenVariantEggIsSuppressed() {
        assertTrue(HusbandryEggs.isVanillaEgg(Material.EGG));
        assertTrue(HusbandryEggs.isVanillaEgg(Material.BLUE_EGG));
        assertTrue(HusbandryEggs.isVanillaEgg(Material.BROWN_EGG));
        assertFalse(HusbandryEggs.isVanillaEgg(Material.FEATHER));
    }
}
