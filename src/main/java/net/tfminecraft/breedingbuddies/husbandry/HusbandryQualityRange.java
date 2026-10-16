package net.tfminecraft.breedingbuddies.husbandry;

import java.util.List;
import java.util.Random;

public final class HusbandryQualityRange {

    public record Bounds(int min, int max) {}

    private HusbandryQualityRange() {}

    public static int starsForGenetics(int genetics, List<HusbandryQualityBand> bands) {
        int stars = 1;
        if (bands != null) {
            for (HusbandryQualityBand band : bands) {
                if (genetics >= band.minGenetics()) {
                    stars = band.stars();
                }
            }
        }
        return Math.max(1, Math.min(5, stars));
    }

    public static int effectiveGenetics(int genetics, int care, int careMax) {
        int maxCare = Math.max(1, careMax);
        int clampedCare = Math.max(0, care);
        return (int) Math.floor(genetics * (clampedCare / (double) maxCare));
    }

    public static Bounds of(int genetics, int care, int careMax, List<HusbandryQualityBand> bands) {
        int max = starsForGenetics(genetics, bands);
        int min = starsForGenetics(effectiveGenetics(genetics, care, careMax), bands);
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        return new Bounds(min, max);
    }

    public static int roll(Bounds bounds, Random random) {
        if (bounds == null) {
            return 1;
        }
        int min = bounds.min();
        int max = bounds.max();
        if (min >= max) {
            return min;
        }
        Random rng = random == null ? new Random() : random;
        return min + rng.nextInt(max - min + 1);
    }
}
