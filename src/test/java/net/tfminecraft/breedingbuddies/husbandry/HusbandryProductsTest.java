package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class HusbandryProductsTest {

    @Test
    void emptySpeciesHasNoProducts() {
        assertFalse(HusbandryProducts.hasProducts(null));
        assertFalse(HusbandryProducts.hasProducts(species(false, "", empty(), empty(), empty(), "")));
    }

    @Test
    void slaughterShowsProducts() {
        HusbandrySpecies species = species(
                false,
                "food(type=roast;origin=PORK)",
                empty(),
                empty(),
                empty(),
                "");
        assertTrue(HusbandryProducts.hasProducts(species));
        assertEquals(List.of("On slaughter"), HusbandryProducts.modeLines(species));
    }

    @Test
    void chickenListsSlaughterShedAndEggs() {
        HusbandrySpecies species = species(
                false,
                "food(type=roast;origin=CHICKEN)",
                empty(),
                empty(),
                countedTable(),
                "vanilla");
        assertTrue(HusbandryProducts.hasProducts(species));
        assertEquals(List.of("On slaughter", "Shed", "Eggs"), HusbandryProducts.modeLines(species));
    }

    @Test
    void capabilityFlagsFollowConfigSections() {
        HusbandrySpecies shearOnly = species(false, "", empty(), countedTable(), empty(), "");
        assertFalse(shearOnly.canSlaughter());
        assertTrue(shearOnly.canShear());
        assertFalse(shearOnly.canMilk());

        HusbandrySpecies milkOnly = species(true, "", empty(), empty(), empty(), "");
        assertTrue(milkOnly.canMilk());
        assertFalse(milkOnly.canSlaughter());
    }

    @Test
    void cowListsSlaughterAndMilk() {
        HusbandrySpecies species = species(
                true,
                "food(type=roast;origin=BEEF)",
                countedTable(),
                empty(),
                empty(),
                "");
        assertEquals(List.of("On slaughter", "Milk"), HusbandryProducts.modeLines(species));
    }

    private static HusbandrySpecies species(
            boolean milk,
            String slaughterMeat,
            HusbandryDropTable slaughterDrops,
            HusbandryDropTable shearDrops,
            HusbandryDropTable shedDrops,
            String egg) {
        return new HusbandrySpecies(null, milk, slaughterMeat, slaughterDrops, shearDrops, shedDrops, egg, 0, 0, 0);
    }

    private static HusbandryDropTable empty() {
        return HusbandryDropTable.empty();
    }

    private static HusbandryDropTable countedTable() {
        return new HusbandryDropTable(
                List.of(new HusbandryDropEntry("v.feather", 1, 100)),
                List.of(),
                List.of(),
                List.of(),
                true);
    }
}
