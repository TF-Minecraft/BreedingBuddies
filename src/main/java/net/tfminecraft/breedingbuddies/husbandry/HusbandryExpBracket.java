package net.tfminecraft.breedingbuddies.husbandry;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class HusbandryExpBracket {
    private final int min;
    private final int max;

    private HusbandryExpBracket(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public static HusbandryExpBracket of(int min, int max) {
        int low = Math.max(0, Math.min(min, max));
        int high = Math.max(0, Math.max(min, max));
        return new HusbandryExpBracket(low, high);
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int roll(RandomGenerator random) {
        if (min == max) {
            return min;
        }
        return random.nextInt(min, max + 1);
    }

    public int roll() {
        return roll(ThreadLocalRandom.current());
    }
}
