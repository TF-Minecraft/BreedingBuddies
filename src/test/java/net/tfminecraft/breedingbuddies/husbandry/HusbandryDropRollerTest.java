package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HusbandryDropRollerTest {

    private static final List<HusbandryAmountBand> BANDS = List.of(
            new HusbandryAmountBand(0, 1, 1, 0),
            new HusbandryAmountBand(500, 5, 2, 1),
            new HusbandryAmountBand(750, 7, 3, 2));

    @Test
    void extraIsZeroThenOneThenTwo() {
        assertEquals(0, HusbandryConfig.secondaryExtraFor(0, BANDS));
        assertEquals(0, HusbandryConfig.secondaryExtraFor(499, BANDS));
        assertEquals(1, HusbandryConfig.secondaryExtraFor(500, BANDS));
        assertEquals(2, HusbandryConfig.secondaryExtraFor(750, BANDS));
        assertEquals(1, 1 + HusbandryConfig.secondaryExtraFor(0, BANDS));
        assertEquals(3, 1 + HusbandryConfig.secondaryExtraFor(750, BANDS));
    }

    @Test
    void oneStarPoolHasNoLegendary() {
        HusbandryDropTable table = new HusbandryDropTable(
                List.of(new HusbandryDropEntry("common", 1, 50)),
                List.of(new HusbandryDropEntry("rare", 1, 5)),
                List.of(new HusbandryDropEntry("epic", 1, 5)),
                List.of(new HusbandryDropEntry("legendary", 1, 5)),
                true);
        List<HusbandryDropEntry> pool = HusbandryDropRoller.unlockedPool(table, 1);
        assertEquals(1, pool.size());
        assertEquals("common", pool.get(0).path());
        List<HusbandryDropEntry> five = HusbandryDropRoller.unlockedPool(table, 5);
        assertEquals(4, five.size());
    }

    @Test
    void countedPicksAreIndependent() {
        List<HusbandryDropEntry> pool = List.of(
                new HusbandryDropEntry("a", 1, 1),
                new HusbandryDropEntry("b", 1, 1));
        Random random = new Random(3L);
        String first = HusbandryDropRoller.pickEntry(pool, random).path();
        String second = HusbandryDropRoller.pickEntry(pool, random).path();
        assertTrue(first.equals("a") || first.equals("b"));
        assertTrue(second.equals("a") || second.equals("b"));
    }

    @Test
    void sheepOnWoolCooldownDropsNoHides() {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "SHEEP", "Test");
        animal.setWoolReadyAt(2_000L);
        assertTrue(HusbandryDropRoller.woolBlocked(animal.type(), animal, 1_000L));
        assertEquals(0, HusbandryDropRoller.hideCount(animal, 1_000L));
        assertFalse(HusbandryDropRoller.woolBlocked(animal.type(), animal, 3_000L));
    }

    @Test
    void rollReturnsEmptyWhenTableMissing() {
        HusbandryAnimal animal = new HusbandryAnimal(UUID.randomUUID(), "SHEEP", "Test");
        assertTrue(HusbandryDropRoller.roll(
                null, animal, new Random(1L), 0L, HusbandryDropRoller.CountMode.WOOL_COUNT).isEmpty());
        assertTrue(HusbandryDropRoller.roll(
                HusbandryDropTable.empty(), animal, new Random(1L), 0L, HusbandryDropRoller.CountMode.SINGLE).isEmpty());
    }

    @Test
    void pigMissPathIsEmptyOptional() {
        HusbandryDropEntry miss = new HusbandryDropEntry("", 1, 95);
        HusbandryDropEntry dust = new HusbandryDropEntry("m.currency.enchanted_dust", 1, 5);
        HusbandryDropEntry picked = HusbandryDropRoller.pickEntry(List.of(miss), new Random(1L));
        assertEquals("", picked.path());
        assertTrue(picked.path().isBlank());
        assertNull(HusbandryDropRoller.pickEntry(List.of(), new Random(1L)));
        assertEquals("m.currency.enchanted_dust", HusbandryDropRoller.pickEntry(List.of(dust), new Random(1L)).path());
    }
}
