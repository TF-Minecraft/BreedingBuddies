package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class HusbandryQualityRangeTest {

    private static final List<HusbandryQualityBand> BANDS = List.of(
            new HusbandryQualityBand(0, 1),
            new HusbandryQualityBand(200, 2),
            new HusbandryQualityBand(400, 3),
            new HusbandryQualityBand(600, 4),
            new HusbandryQualityBand(800, 5));

    @Test
    void highGeneticsZeroCareIsOneToFive() {
        HusbandryQualityRange.Bounds range = HusbandryQualityRange.of(1000, 0, 200, BANDS);
        assertEquals(1, range.min());
        assertEquals(5, range.max());
    }

    @Test
    void highGeneticsFullCareIsFiveToFive() {
        HusbandryQualityRange.Bounds range = HusbandryQualityRange.of(1000, 200, 200, BANDS);
        assertEquals(5, range.min());
        assertEquals(5, range.max());
    }

    @Test
    void twoHundredGeneticsZeroCareIsOneToTwo() {
        HusbandryQualityRange.Bounds range = HusbandryQualityRange.of(200, 0, 200, BANDS);
        assertEquals(1, range.min());
        assertEquals(2, range.max());
    }

    @Test
    void rollStaysInsideRange() {
        HusbandryQualityRange.Bounds range = new HusbandryQualityRange.Bounds(2, 4);
        Random random = new Random(1L);
        for (int i = 0; i < 50; i++) {
            int rolled = HusbandryQualityRange.roll(range, random);
            assertTrue(rolled >= 2 && rolled <= 4, "rolled " + rolled);
        }
    }
}
