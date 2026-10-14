package net.tfminecraft.breedingbuddies.husbandry;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.EntityType;

public final class HusbandryConfig {

    private static int maxAnimals = 15;
    private static int careMax = 200;
    private static int careUpIntervalSeconds = 3600;
    private static double careUpAmount = 1;
    private static int careDownIntervalSeconds = 3600;
    private static double careDownAmount = 1;
    private static int decayGraceSeconds = 86400;
    private static int offlineCareSeconds = 28800;
    private static int longUnloadForceSeconds = 28800;
    private static int minLoadedSeconds = 60;
    private static double afflictionMeanHours = 6;
    private static double afflictionMinHours = 4;
    private static double afflictionMaxHours = 8;
    private static int milkCooldownSeconds = 1200;
    private static int initialGeneticMax = 20;
    private static int maxGenetics = 1000;
    private static int minRoastCuts = 1;
    private static int woolTimerSeconds = 1200;
    private static int growUpSeconds = 3600;
    private static int shedTimerSeconds = 28800;
    private static double shedChance = 0.15;
    private static int eggTimerSeconds = 3600;
    private static String tameItem = "";
    private static String coOwnItem = "";
    private static String feedItem = "";
    private static String gloveItem = "";
    private static String neuterItem = "";
    private static String inspectItem = "";
    private static String mountStatsItem = "";
    private static Set<EntityType> removeUnowned = Set.of();
    private static Map<EntityType, HusbandrySpecies> species = Map.of();
    private static boolean damageOtherPlayers = true;
    private static boolean damageOwner = true;
    private static boolean damageMobs = true;
    private static boolean damageEnvironment = true;
    private static List<HusbandryQualityBand> qualityBands = List.of();
    private static Map<EntityType, HusbandryMountStats> mounts = Map.of();
    private static double geneticVarianceMultiplier = 1;
    private static double geneticSlowdownDivisor = 1;
    private static double careInfluence = 0.02;
    private static String statsRevision = "1";
    private static boolean restoreLostAnimals = true;
    private static List<HusbandryAmountBand> amountBands = List.of();
    private static double mountSpeedMinPct = 0.40;
    private static double mountSpeedGeneticsPct = 0.30;
    private static double mountSpeedCarePct = 0.20;
    private static String professionId = "farming";
    private static HusbandryExpBracket defaultExp = HusbandryExpBracket.of(6, 8);

    private HusbandryConfig() {}

    public static void apply(
            int maxAnimalsValue,
            int careMaxValue,
            int careUpIntervalSecondsValue,
            double careUpAmountValue,
            int careDownIntervalSecondsValue,
            double careDownAmountValue,
            int decayGraceSecondsValue,
            int offlineCareSecondsValue,
            int longUnloadForceSecondsValue,
            int minLoadedSecondsValue,
            double afflictionMeanHoursValue,
            double afflictionMinHoursValue,
            double afflictionMaxHoursValue,
            int milkCooldownSecondsValue,
            int initialGeneticMaxValue,
            int maxGeneticsValue,
            int minRoastCutsValue,
            int woolTimerSecondsValue,
            int growUpSecondsValue,
            int shedTimerSecondsValue,
            double shedChanceValue,
            int eggTimerSecondsValue,
            String tameItemValue,
            String coOwnItemValue,
            String feedItemValue,
            String gloveItemValue,
            String neuterItemValue,
            String inspectItemValue,
            String mountStatsItemValue,
            Set<EntityType> removeUnownedValue,
            Map<EntityType, HusbandrySpecies> speciesValue,
            boolean damageOtherPlayersValue,
            boolean damageOwnerValue,
            boolean damageMobsValue,
            boolean damageEnvironmentValue,
            List<HusbandryQualityBand> qualityBandsValue,
            Map<EntityType, HusbandryMountStats> mountsValue,
            double geneticVarianceMultiplierValue,
            double geneticSlowdownDivisorValue,
            List<HusbandryAmountBand> amountBandsValue) {
        maxAnimals = Math.max(1, maxAnimalsValue);
        careMax = Math.max(1, careMaxValue);
        careUpIntervalSeconds = Math.max(1, careUpIntervalSecondsValue);
        careUpAmount = careUpAmountValue;
        careDownIntervalSeconds = Math.max(1, careDownIntervalSecondsValue);
        careDownAmount = careDownAmountValue;
        decayGraceSeconds = Math.max(0, decayGraceSecondsValue);
        offlineCareSeconds = Math.max(0, offlineCareSecondsValue);
        longUnloadForceSeconds = Math.max(0, longUnloadForceSecondsValue);
        minLoadedSeconds = Math.max(0, minLoadedSecondsValue);
        afflictionMeanHours = afflictionMeanHoursValue;
        afflictionMinHours = afflictionMinHoursValue;
        afflictionMaxHours = Math.max(afflictionMinHoursValue, afflictionMaxHoursValue);
        milkCooldownSeconds = Math.max(0, milkCooldownSecondsValue);
        initialGeneticMax = Math.max(0, initialGeneticMaxValue);
        maxGenetics = Math.max(1, maxGeneticsValue);
        minRoastCuts = Math.max(0, minRoastCutsValue);
        woolTimerSeconds = Math.max(0, woolTimerSecondsValue);
        growUpSeconds = Math.max(0, growUpSecondsValue);
        shedTimerSeconds = Math.max(0, shedTimerSecondsValue);
        shedChance = Math.max(0, Math.min(1, shedChanceValue));
        eggTimerSeconds = Math.max(0, eggTimerSecondsValue);
        tameItem = tameItemValue == null ? "" : tameItemValue;
        coOwnItem = coOwnItemValue == null ? "" : coOwnItemValue;
        feedItem = feedItemValue == null ? "" : feedItemValue;
        gloveItem = gloveItemValue == null ? "" : gloveItemValue;
        neuterItem = neuterItemValue == null ? "" : neuterItemValue;
        inspectItem = inspectItemValue == null ? "" : inspectItemValue;
        mountStatsItem = mountStatsItemValue == null ? "" : mountStatsItemValue;
        removeUnowned = Set.copyOf(removeUnownedValue);
        species = Map.copyOf(speciesValue);
        damageOtherPlayers = damageOtherPlayersValue;
        damageOwner = damageOwnerValue;
        damageMobs = damageMobsValue;
        damageEnvironment = damageEnvironmentValue;
        qualityBands = List.copyOf(qualityBandsValue);
        mounts = Map.copyOf(mountsValue);
        geneticVarianceMultiplier = geneticVarianceMultiplierValue;
        geneticSlowdownDivisor = geneticSlowdownDivisorValue;
        amountBands = List.copyOf(amountBandsValue);
    }

    public static void setMountSpeedShares(double minPct, double geneticsPct, double carePct) {
        mountSpeedMinPct = Math.max(0, minPct);
        mountSpeedGeneticsPct = Math.max(0, geneticsPct);
        mountSpeedCarePct = Math.max(0, carePct);
    }

    public static void setBreeding(double multiplier, double slowdown, double careInfluenceValue) {
        geneticVarianceMultiplier = Math.max(0, multiplier);
        geneticSlowdownDivisor = slowdown;
        careInfluence = Math.max(0, careInfluenceValue);
    }

    public static void setStatsRevision(String revision) {
        statsRevision = revision == null ? "" : revision.trim();
    }

    public static void setRestoreLostAnimals(boolean enabled) {
        restoreLostAnimals = enabled;
    }

    public static boolean restoreLostAnimals() {
        return restoreLostAnimals;
    }

    public static void setProfessionExp(String profession, HusbandryExpBracket bracket) {
        professionId = profession == null ? "" : profession.trim();
        defaultExp = bracket == null ? HusbandryExpBracket.of(6, 8) : bracket;
    }

    public static String professionId() {
        return professionId;
    }

    public static HusbandryExpBracket expFor(EntityType type) {
        if (type == null) {
            return defaultExp;
        }
        HusbandrySpecies configured = species(type);
        if (configured != null && configured.exp() != null) {
            return configured.exp();
        }
        return defaultExp;
    }

    public static String statsRevision() {
        return statsRevision;
    }

    public static double mountSpeedMinPct() {
        return mountSpeedMinPct;
    }

    public static double mountSpeedGeneticsPct() {
        return mountSpeedGeneticsPct;
    }

    public static double mountSpeedCarePct() {
        return mountSpeedCarePct;
    }

    public static int maxAnimals() {
        return maxAnimals;
    }

    public static int careMax() {
        return careMax;
    }

    public static int careUpIntervalSeconds() {
        return careUpIntervalSeconds;
    }

    public static double careUpAmount() {
        return careUpAmount;
    }

    public static int careDownIntervalSeconds() {
        return careDownIntervalSeconds;
    }

    public static double careDownAmount() {
        return careDownAmount;
    }

    public static int decayGraceSeconds() {
        return decayGraceSeconds;
    }

    public static int offlineCareSeconds() {
        return offlineCareSeconds;
    }

    public static int longUnloadForceSeconds() {
        return longUnloadForceSeconds;
    }

    public static int minLoadedSeconds() {
        return minLoadedSeconds;
    }

    public static double afflictionMeanHours() {
        return afflictionMeanHours;
    }

    public static double afflictionMinHours() {
        return afflictionMinHours;
    }

    public static double afflictionMaxHours() {
        return afflictionMaxHours;
    }

    public static int milkCooldownSeconds() {
        return milkCooldownSeconds;
    }

    public static int milkTimerSeconds() {
        return milkCooldownSeconds;
    }

    public static int milkTimerSeconds(EntityType type) {
        if (type != null) {
            HusbandrySpecies configured = species.get(type);
            if (configured != null && configured.milkTimerSeconds() > 0) {
                return configured.milkTimerSeconds();
            }
        }
        return milkCooldownSeconds;
    }

    public static int resolveMilkTimerSeconds(int speciesOverrideSeconds, int globalSeconds) {
        return speciesOverrideSeconds > 0 ? speciesOverrideSeconds : Math.max(0, globalSeconds);
    }

    public static int initialGeneticMax() {
        return initialGeneticMax;
    }

    public static int maxGenetics() {
        return maxGenetics;
    }

    public static int minRoastCuts() {
        return minRoastCuts;
    }

    public static int woolTimerSeconds() {
        return woolTimerSeconds;
    }

    public static int woolTimerSeconds(EntityType type) {
        if (type != null) {
            HusbandrySpecies configured = species.get(type);
            if (configured != null && configured.woolTimerSeconds() > 0) {
                return configured.woolTimerSeconds();
            }
        }
        return woolTimerSeconds;
    }

    public static int resolveWoolTimerSeconds(int speciesOverrideSeconds, int globalSeconds) {
        return speciesOverrideSeconds > 0 ? speciesOverrideSeconds : Math.max(0, globalSeconds);
    }

    public static int growUpSeconds() {
        return growUpSeconds;
    }

    public static int growUpSeconds(EntityType type) {
        if (type != null) {
            HusbandrySpecies configured = species.get(type);
            if (configured != null && configured.growUpSeconds() > 0) {
                return configured.growUpSeconds();
            }
        }
        return growUpSeconds;
    }

    public static int shedTimerSeconds() {
        return shedTimerSeconds;
    }

    public static double shedChance() {
        return shedChance;
    }

    public static int eggTimerSeconds() {
        return eggTimerSeconds;
    }

    public static String tameItem() {
        return tameItem;
    }

    public static String coOwnItem() {
        return coOwnItem;
    }

    public static String feedItem() {
        return feedItem;
    }

    public static String gloveItem() {
        return gloveItem;
    }

    public static String neuterItem() {
        return neuterItem;
    }

    public static String inspectItem() {
        return inspectItem;
    }

    public static String mountStatsItem() {
        return mountStatsItem;
    }

    public static Set<EntityType> removeUnowned() {
        return removeUnowned;
    }

    public static boolean isRemoveUnowned(EntityType type) {
        return type != null && removeUnowned.contains(type);
    }

    public static Map<EntityType, HusbandrySpecies> species() {
        return species;
    }

    public static HusbandrySpecies species(EntityType type) {
        return species.get(type);
    }

    public static boolean damageOtherPlayers() {
        return damageOtherPlayers;
    }

    public static boolean damageOwner() {
        return damageOwner;
    }

    public static boolean damageMobs() {
        return damageMobs;
    }

    public static boolean damageEnvironment() {
        return damageEnvironment;
    }

    public static List<HusbandryQualityBand> qualityBands() {
        return qualityBands;
    }

    public static int starsForGenetics(int genetics) {
        return HusbandryQualityRange.starsForGenetics(genetics, qualityBands);
    }

    public static HusbandryQualityRange.Bounds qualityRange(HusbandryAnimal animal) {
        if (animal == null) {
            return new HusbandryQualityRange.Bounds(1, 1);
        }
        return HusbandryQualityRange.of(animal.genetics(), animal.care(), careMax, qualityBands);
    }

    public static Map<EntityType, HusbandryMountStats> mounts() {
        return mounts;
    }

    public static HusbandryMountStats mountStats(EntityType type) {
        return mounts.get(type);
    }

    public static double geneticVarianceMultiplier() {
        return geneticVarianceMultiplier;
    }

    public static double geneticSlowdownDivisor() {
        return geneticSlowdownDivisor;
    }

    public static double careInfluence() {
        return careInfluence;
    }

    public static boolean isHusbandryType(EntityType type) {
        return type != null && (species.containsKey(type) || removeUnowned.contains(type));
    }

    public static int effectiveGenetics(HusbandryAnimal animal) {
        if (animal == null) {
            return 0;
        }
        return (int) Math.floor(animal.genetics() * (animal.care() / (double) careMax));
    }

    public static int roastCutsFor(int effectiveGenetics) {
        int cuts = minRoastCuts;
        for (HusbandryAmountBand band : amountBands) {
            if (effectiveGenetics >= band.minGenetics()) {
                cuts = band.roastCuts();
            }
        }
        return Math.max(minRoastCuts, cuts);
    }

    public static int secondaryExtraFor(int effectiveGenetics) {
        return secondaryExtraFor(effectiveGenetics, amountBands);
    }

    public static int secondaryExtraFor(int effectiveGenetics, List<HusbandryAmountBand> bands) {
        int extra = 0;
        if (bands != null) {
            for (HusbandryAmountBand band : bands) {
                if (effectiveGenetics >= band.minGenetics()) {
                    extra = band.secondaryExtra();
                }
            }
        }
        return Math.max(0, Math.min(2, extra));
    }

    public static int hideCount(int effectiveGenetics) {
        return 1 + secondaryExtraFor(effectiveGenetics);
    }

    public static int woolFor(int effectiveGenetics) {
        int wool = 1;
        for (HusbandryAmountBand band : amountBands) {
            if (effectiveGenetics >= band.minGenetics()) {
                wool = band.wool();
            }
        }
        return Math.max(1, wool);
    }
}
