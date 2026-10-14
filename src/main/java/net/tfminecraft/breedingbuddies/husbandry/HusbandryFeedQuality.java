package net.tfminecraft.breedingbuddies.husbandry;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.cooking.api.CookingFoodApi;

/**
 * Breeding-food stars scale the gene bonus above the parent average.
 * Five stars, universal feed, and items with no cooking quality keep the full bonus.
 */
public final class HusbandryFeedQuality {

    public static final double FULL_SCALE = 1.0;
    private static final long OFFER_WINDOW_MILLIS = 2_000L;
    private static final long LOVE_WINDOW_MILLIS = 45_000L;

    private static final ConcurrentHashMap<UUID, TimedScale> OFFERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, TimedScale> FEEDS = new ConcurrentHashMap<>();

    private HusbandryFeedQuality() {}

    public static double scaleForStars(int stars) {
        int clamped = Math.max(1, Math.min(5, stars));
        return clamped / 5.0;
    }

    public static double clampScale(double scale) {
        if (Double.isNaN(scale)) {
            return FULL_SCALE;
        }
        return Math.max(0, Math.min(FULL_SCALE, scale));
    }

    public static double combine(Double left, Double right) {
        if (left == null && right == null) {
            return FULL_SCALE;
        }
        if (left == null) {
            return clampScale(right);
        }
        if (right == null) {
            return clampScale(left);
        }
        return clampScale((left + right) / 2.0);
    }

    public static double scaleOf(ItemStack stack) {
        if (isUniversalFeed(stack)) {
            return FULL_SCALE;
        }
        OptionalInt stars = CookingFoodApi.qualityOf(stack);
        if (stars.isEmpty()) {
            return FULL_SCALE;
        }
        return scaleForStars(stars.getAsInt());
    }

    public static void offer(UUID animalId, double scale, long nowMillis) {
        if (animalId == null) {
            return;
        }
        OFFERS.put(animalId, new TimedScale(clampScale(scale), nowMillis));
    }

    public static void discardOffer(UUID animalId) {
        if (animalId != null) {
            OFFERS.remove(animalId);
        }
    }

    public static void commitOffer(UUID animalId, long nowMillis) {
        if (animalId == null) {
            return;
        }
        TimedScale offer = OFFERS.remove(animalId);
        if (offer == null || nowMillis - offer.atMillis > OFFER_WINDOW_MILLIS) {
            return;
        }
        remember(animalId, offer.scale, nowMillis);
    }

    public static void remember(UUID animalId, double scale, long nowMillis) {
        if (animalId == null) {
            return;
        }
        FEEDS.put(animalId, new TimedScale(clampScale(scale), nowMillis));
    }

    public static double consumeBreedingScale(UUID mother, UUID father, ItemStack bredWith, long nowMillis) {
        Double motherScale = take(mother, nowMillis);
        Double fatherScale = take(father, nowMillis);
        if (motherScale == null && fatherScale == null) {
            return scaleOf(bredWith);
        }
        return combine(motherScale, fatherScale);
    }

    public static double takePair(UUID mother, UUID father, long nowMillis) {
        return combine(take(mother, nowMillis), take(father, nowMillis));
    }

    static void clear() {
        OFFERS.clear();
        FEEDS.clear();
    }

    private static Double take(UUID animalId, long nowMillis) {
        if (animalId == null) {
            return null;
        }
        TimedScale feed = FEEDS.remove(animalId);
        if (feed == null || nowMillis - feed.atMillis > LOVE_WINDOW_MILLIS) {
            return null;
        }
        return feed.scale;
    }

    private static boolean isUniversalFeed(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        try {
            return HusbandryItems.matches(stack, HusbandryConfig.feedItem());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record TimedScale(double scale, long atMillis) {}
}
