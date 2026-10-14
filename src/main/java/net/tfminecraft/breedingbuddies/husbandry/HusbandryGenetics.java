package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Random;

public final class HusbandryGenetics {

    private HusbandryGenetics() {}

    public static int roll(
            int motherGenetics,
            int fatherGenetics,
            int motherCare,
            int fatherCare,
            Random random) {
        return roll(motherGenetics, fatherGenetics, motherCare, fatherCare, random, HusbandryFeedQuality.FULL_SCALE);
    }

    public static int roll(
            int motherGenetics,
            int fatherGenetics,
            int motherCare,
            int fatherCare,
            Random random,
            double feedScale) {
        int maxGenetics = HusbandryConfig.maxGenetics();
        int avg = (motherGenetics + fatherGenetics) / 2;
        double varianceBase = maxGenetics / 10.0;
        int minVariance = Math.max(1, maxGenetics / 100);
        double divider = 10.0 * Math.max(0.0001, HusbandryConfig.geneticSlowdownDivisor());
        int variance = Math.max(
                minVariance,
                (int) (HusbandryConfig.geneticVarianceMultiplier() * (varianceBase - avg / divider)));
        int bonus = random == null || variance <= 0 ? 0 : random.nextInt(variance);
        int careMax = HusbandryConfig.careMax();
        double careAvg = (motherCare + fatherCare) / 2.0;
        double careRatio = careMax <= 0 ? 0 : Math.max(0, Math.min(1, careAvg / careMax));
        int careExtra = (int) (HusbandryConfig.careInfluence() * maxGenetics * careRatio);
        int boost = (int) (bonus * careRatio) + careExtra;
        int rolled = avg + (int) (boost * HusbandryFeedQuality.clampScale(feedScale));
        return Math.max(0, Math.min(maxGenetics, rolled));
    }
}
