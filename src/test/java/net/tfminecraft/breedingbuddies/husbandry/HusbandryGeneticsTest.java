package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HusbandryGeneticsTest {

    @BeforeEach
    void breedingDefaults() {
        HusbandryConfig.setBreeding(0.4, 0.4, 0.02);
    }

    @Test
    void lowGeneMaxCareChildStaysFarBelowCap() {
        int child = HusbandryGenetics.roll(10, 10, 200, 200, maxBonus());
        assertTrue(child < 100, "child " + child);
        assertEquals(68, child);
    }

    @Test
    void zeroCareStaysAtParentAverage() {
        Random random = new Random(1L);
        for (int i = 0; i < 50; i++) {
            assertEquals(10, HusbandryGenetics.roll(10, 10, 0, 0, random));
            assertEquals(15, HusbandryGenetics.roll(10, 21, 0, 0, random));
        }
    }

    @Test
    void maxCareBeatsZeroCareWhenBonusIsNonZero() {
        int zeroCare = HusbandryGenetics.roll(10, 10, 0, 0, maxBonus());
        int maxCare = HusbandryGenetics.roll(10, 10, 200, 200, maxBonus());
        assertEquals(10, zeroCare);
        assertTrue(maxCare > zeroCare, "maxCare " + maxCare);
    }

    @Test
    void resultIsClampedToMaxGenetics() {
        int child = HusbandryGenetics.roll(1000, 1000, 200, 200, maxBonus());
        assertEquals(1000, child);
    }

    @Test
    void resultIsClampedToZero() {
        int child = HusbandryGenetics.roll(-50, -50, 0, 0, maxBonus());
        assertEquals(0, child);
    }

    @Test
    void fiveStarFeedMatchesTheUnscaledRoll() {
        int full = HusbandryGenetics.roll(10, 10, 200, 200, maxBonus());
        int fiveStars = HusbandryGenetics.roll(
                10, 10, 200, 200, maxBonus(), HusbandryFeedQuality.scaleForStars(5));
        assertEquals(68, full);
        assertEquals(full, fiveStars);
    }

    @Test
    void oneStarFeedKeepsOneFifthOfTheBoost() {
        int child = HusbandryGenetics.roll(
                10, 10, 200, 200, maxBonus(), HusbandryFeedQuality.scaleForStars(1));
        assertEquals(21, child);
    }

    @Test
    void mixedOneAndFiveStarFeedsUseTheAverage() {
        double scale = HusbandryFeedQuality.combine(
                HusbandryFeedQuality.scaleForStars(1),
                HusbandryFeedQuality.scaleForStars(5));
        int child = HusbandryGenetics.roll(10, 10, 200, 200, maxBonus(), scale);
        assertEquals(44, child);
    }

    @Test
    void zeroCareIgnoresFeedQuality() {
        assertEquals(10, HusbandryGenetics.roll(
                10, 10, 0, 0, maxBonus(), HusbandryFeedQuality.scaleForStars(1)));
    }

    private static Random maxBonus() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return Math.max(0, bound - 1);
            }
        };
    }
}
