package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class HusbandrySimulator {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private HusbandrySimulator() {}

    public static boolean visitLongEnough(HusbandryAnimal animal, long nowMillis) {
        if (animal == null) {
            return false;
        }
        Long visitStart = animal.loadedVisitStart();
        if (visitStart == null) {
            return false;
        }
        long minMillis = HusbandryConfig.minLoadedSeconds() * 1000L;
        return nowMillis - visitStart >= minMillis;
    }

    public static boolean isHappy(HusbandryAnimal animal) {
        return animal != null && animal.hungrySince() == null && animal.dirtySince() == null;
    }

    public static void catchUp(HusbandryAnimal animal, long nowMillis, Random random) {
        simulate(animal, nowMillis, random, true);
    }

    public static void tickLoaded(HusbandryAnimal animal, long nowMillis) {
        tickLoaded(animal, nowMillis, ThreadLocalRandom.current());
    }

    public static void tickLoaded(HusbandryAnimal animal, long nowMillis, Random random) {
        simulate(animal, nowMillis, random, false);
    }

    public static void resetAfflictionCycle(HusbandryAnimal animal, Random random) {
        if (animal == null) {
            return;
        }
        animal.setAfflictionElapsed(0);
        animal.setAfflictionAt(rollAfflictionHours(random));
    }

    public static void clearHungry(HusbandryAnimal animal, Random random) {
        if (animal == null) {
            return;
        }
        animal.setHungrySince(null);
        if (isHappy(animal)) {
            resetAfflictionCycle(animal, random);
        }
    }

    public static void clearDirty(HusbandryAnimal animal, Random random) {
        if (animal == null) {
            return;
        }
        animal.setDirtySince(null);
        if (isHappy(animal)) {
            resetAfflictionCycle(animal, random);
        }
    }

    private static void simulate(HusbandryAnimal animal, long nowMillis, Random random, boolean includeUnloaded) {
        if (animal == null) {
            return;
        }
        Random rng = random == null ? ThreadLocalRandom.current() : random;
        if (nowMillis < animal.lastProcessedAt()) {
            animal.setLastProcessedAt(nowMillis);
            return;
        }

        long elapsedMillis = nowMillis - animal.lastProcessedAt();
        long unloadedMillis = 0L;
        if (includeUnloaded && animal.unloadedAt() != null) {
            unloadedMillis = Math.min(elapsedMillis, Math.max(0L, nowMillis - animal.unloadedAt()));
        }
        long loadedMillis = Math.max(0L, elapsedMillis - unloadedMillis);
        double loadedHours = loadedMillis / (double) MILLIS_PER_HOUR;

        if (unloadedMillis / 1000L > HusbandryConfig.longUnloadForceSeconds()) {
            applyLongUnloadForce(animal, nowMillis, rng);
        }
        applyAffliction(animal, loadedHours, nowMillis, rng);
        applyDecay(animal, animal.lastProcessedAt(), nowMillis);
        animal.setLastProcessedAt(nowMillis);
    }

    private static void applyDecay(HusbandryAnimal animal, long lastProcessed, long nowMillis) {
        Long hungry = animal.hungrySince();
        Long dirty = animal.dirtySince();
        if (hungry == null && dirty == null) {
            return;
        }
        long earliest = hungry == null ? dirty : (dirty == null ? hungry : Math.min(hungry, dirty));
        long graceMillis = HusbandryConfig.decayGraceSeconds() * 1000L;
        long decayStart = earliest + graceMillis;
        long decayFrom = Math.max(lastProcessed, decayStart);
        if (nowMillis <= decayFrom) {
            return;
        }
        long decaySeconds = (nowMillis - decayFrom) / 1000L;
        applyDecayJumps(animal, decaySeconds, HusbandryConfig.careDownIntervalSeconds(), HusbandryConfig.careDownAmount());
    }

    /** Trust is only earned by care actions (HusbandryTrust); time only takes it away from neglected animals. */
    private static void applyDecayJumps(HusbandryAnimal animal, long elapsedSeconds, int intervalSeconds, double amount) {
        if (elapsedSeconds <= 0 || intervalSeconds <= 0 || amount <= 0) {
            return;
        }
        long remainder = animal.careDownRemainderSeconds() + elapsedSeconds;
        long jumps = remainder / intervalSeconds;
        animal.setCareDownRemainderSeconds(remainder % intervalSeconds);
        if (jumps <= 0) {
            return;
        }
        addCare(animal, -jumps * amount);
    }

    private static void applyLongUnloadForce(HusbandryAnimal animal, long nowMillis, Random random) {
        boolean hungry = animal.hungrySince() != null;
        boolean dirty = animal.dirtySince() != null;
        if (hungry && dirty) {
            resetAfflictionCycle(animal, random);
            return;
        }
        if (!hungry && !dirty) {
            if (random.nextBoolean()) {
                animal.setHungrySince(nowMillis);
            } else {
                animal.setDirtySince(nowMillis);
            }
        } else if (!hungry) {
            animal.setHungrySince(nowMillis);
        } else {
            animal.setDirtySince(nowMillis);
        }
        resetAfflictionCycle(animal, random);
    }

    private static void applyAffliction(HusbandryAnimal animal, double loadedHours, long nowMillis, Random random) {
        if (loadedHours <= 0) {
            return;
        }
        if (animal.afflictionAt() <= 0) {
            animal.setAfflictionAt(rollAfflictionHours(random));
        }
        animal.setAfflictionElapsed(animal.afflictionElapsed() + loadedHours);
        while (animal.hungrySince() == null || animal.dirtySince() == null) {
            if (animal.afflictionElapsed() < animal.afflictionAt()) {
                break;
            }
            applyMissingState(animal, nowMillis, random);
            animal.setAfflictionElapsed(animal.afflictionElapsed() - animal.afflictionAt());
            animal.setAfflictionAt(rollAfflictionHours(random));
        }
        if (animal.hungrySince() != null && animal.dirtySince() != null) {
            animal.setAfflictionElapsed(0);
        }
    }

    private static void applyMissingState(HusbandryAnimal animal, long nowMillis, Random random) {
        boolean hungry = animal.hungrySince() != null;
        boolean dirty = animal.dirtySince() != null;
        if (!hungry && !dirty) {
            if (random.nextBoolean()) {
                animal.setHungrySince(nowMillis);
            } else {
                animal.setDirtySince(nowMillis);
            }
            return;
        }
        if (!hungry) {
            animal.setHungrySince(nowMillis);
        } else {
            animal.setDirtySince(nowMillis);
        }
    }

    private static double rollAfflictionHours(Random random) {
        double min = HusbandryConfig.afflictionMinHours();
        double max = HusbandryConfig.afflictionMaxHours();
        if (max <= min) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }

    private static void addCare(HusbandryAnimal animal, double delta) {
        int next = (int) Math.round(animal.care() + delta);
        int max = HusbandryConfig.careMax();
        animal.setCare(Math.max(0, Math.min(max, next)));
    }
}
