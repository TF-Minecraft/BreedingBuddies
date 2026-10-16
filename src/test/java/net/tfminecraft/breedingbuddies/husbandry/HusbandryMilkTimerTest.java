package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HusbandryMilkTimerTest {

    @Test
    void speciesOverrideBeatsDefault() {
        assertEquals(2400, HusbandryConfig.resolveMilkTimerSeconds(2400, 1200));
        assertEquals(1200, HusbandryConfig.resolveMilkTimerSeconds(0, 1200));
        assertEquals(1200, HusbandryConfig.resolveMilkTimerSeconds(-1, 1200));
    }
}
