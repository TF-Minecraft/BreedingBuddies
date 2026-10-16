package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HusbandryExpBracketTest {

    @Test
    void rollStaysInsideAnInclusiveBracket() {
        HusbandryExpBracket reversed = HusbandryExpBracket.of(8, 6);
        assertEquals(6, reversed.min());
        assertEquals(8, reversed.max());
        for (int i = 0; i < 40; i++) {
            int roll = reversed.roll();
            assertTrue(roll >= 6 && roll <= 8);
        }
        assertEquals(6, HusbandryExpBracket.of(6, 6).roll());
    }

    @Test
    void speciesOverrideIsSeparateFromTheDefaultBracket() {
        HusbandryConfig.setProfessionExp("farming", HusbandryExpBracket.of(6, 8));
        HusbandrySpecies cow = new HusbandrySpecies(
                null,
                false,
                "",
                HusbandryDropTable.empty(),
                HusbandryDropTable.empty(),
                HusbandryDropTable.empty(),
                "",
                0,
                0,
                0,
                HusbandryExpBracket.of(5, 10));
        assertEquals(5, cow.exp().min());
        assertEquals(10, cow.exp().max());

        HusbandryExpBracket fallback = HusbandryConfig.expFor(null);
        assertEquals(6, fallback.min());
        assertEquals(8, fallback.max());

        HusbandryConfig.setProfessionExp("  ", HusbandryExpBracket.of(6, 8));
        assertTrue(HusbandryConfig.professionId().isBlank());
    }
}
