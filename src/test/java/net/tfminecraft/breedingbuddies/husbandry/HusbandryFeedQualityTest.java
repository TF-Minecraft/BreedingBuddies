package net.tfminecraft.breedingbuddies.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HusbandryFeedQualityTest {

    @BeforeEach
    void resetFeeds() {
        HusbandryFeedQuality.clear();
    }

    @Test
    void starsScaleLinearlyToAFullBonus() {
        assertEquals(0.2, HusbandryFeedQuality.scaleForStars(1), 1e-9);
        assertEquals(1.0, HusbandryFeedQuality.scaleForStars(5), 1e-9);
        assertEquals(1.0, HusbandryFeedQuality.scaleForStars(9), 1e-9);
    }

    @Test
    void missingFeedsKeepTheFullBonus() {
        assertEquals(1.0, HusbandryFeedQuality.combine(null, null), 1e-9);
        assertEquals(0.2, HusbandryFeedQuality.combine(0.2, null), 1e-9);
    }

    @Test
    void rememberedFeedsAverageUntilTheyExpire() {
        UUID mother = UUID.randomUUID();
        UUID father = UUID.randomUUID();
        long now = 5_000L;
        HusbandryFeedQuality.remember(mother, HusbandryFeedQuality.scaleForStars(1), now);
        HusbandryFeedQuality.remember(father, HusbandryFeedQuality.scaleForStars(5), now);
        assertEquals(0.6, HusbandryFeedQuality.takePair(mother, father, now), 1e-9);
        assertEquals(1.0, HusbandryFeedQuality.takePair(mother, father, now), 1e-9);
    }

    @Test
    void offerCommitsOnlyInsideTheClickWindow() {
        UUID animal = UUID.randomUUID();
        HusbandryFeedQuality.offer(animal, 0.4, 1_000L);
        HusbandryFeedQuality.commitOffer(animal, 1_000L);
        assertEquals(0.4, HusbandryFeedQuality.takePair(animal, null, 1_000L), 1e-9);

        HusbandryFeedQuality.offer(animal, 0.4, 1_000L);
        HusbandryFeedQuality.commitOffer(animal, 4_000L);
        assertEquals(1.0, HusbandryFeedQuality.takePair(animal, null, 4_000L), 1e-9);
    }

    @Test
    void expiredLoveFeedIsIgnored() {
        UUID animal = UUID.randomUUID();
        HusbandryFeedQuality.remember(animal, 0.2, 1_000L);
        assertEquals(1.0, HusbandryFeedQuality.takePair(animal, null, 1_000L + 45_001L), 1e-9);
    }

    @Test
    void clearDropsRememberedFeeds() {
        UUID animal = UUID.randomUUID();
        HusbandryFeedQuality.remember(animal, 0.2, 1_000L);
        HusbandryFeedQuality.clear();
        assertEquals(1.0, HusbandryFeedQuality.takePair(animal, null, 1_000L), 1e-9);
    }
}
