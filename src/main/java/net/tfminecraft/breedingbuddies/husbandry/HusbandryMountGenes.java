package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Random;

/**
 * Mounts carry one gene per stat, so a foal can take its speed from one parent and its jump from the
 * other. The animal's genetics is their average, which drives stars and the menu. Records from before
 * per-stat genes use that genetics for all three until they are bred or reset.
 */
public final class HusbandryMountGenes {

    public enum Stat { HEALTH, SPEED, JUMP }

    private HusbandryMountGenes() {}

    public static int gene(HusbandryAnimal animal, Stat stat) {
        Integer value = switch (stat) {
            case HEALTH -> animal.geneHealth();
            case SPEED -> animal.geneSpeed();
            case JUMP -> animal.geneJump();
        };
        return value == null ? animal.genetics() : value;
    }

    public static void set(HusbandryAnimal animal, int health, int speed, int jump) {
        int max = HusbandryConfig.maxGenetics();
        int h = clamp(health, max), s = clamp(speed, max), j = clamp(jump, max);
        animal.setMountGenes(h, s, j);
        animal.setGenetics((int) Math.round((h + s + j) / 3.0));
    }

    /** Wild and spawn-egg mounts roll each stat on its own, like other wild genetics. */
    public static void rollWild(HusbandryAnimal animal, Random random) {
        int max = HusbandryConfig.initialGeneticMax();
        set(animal, roll(max, random), roll(max, random), roll(max, random));
    }

    /**
     * Each stat is inherited on its own. Parents' trust and the breeding food's stars open the room above
     * the better parent; without them a foal cannot beat the better parent.
     */
    public static void inherit(HusbandryAnimal foal, HusbandryAnimal mother, HusbandryAnimal father,
            double feedScale, Random random) {
        int careMax = HusbandryConfig.careMax();
        double trust = careMax <= 0 ? 0 : Math.max(0, Math.min(1, (mother.care() + father.care()) / 2.0 / careMax));
        double room = trust * HusbandryFeedQuality.clampScale(feedScale);
        set(foal,
                inheritStat(gene(mother, Stat.HEALTH), gene(father, Stat.HEALTH), room, random),
                inheritStat(gene(mother, Stat.SPEED), gene(father, Stat.SPEED), room, random),
                inheritStat(gene(mother, Stat.JUMP), gene(father, Stat.JUMP), room, random));
    }

    /**
     * BreedingBuddies' inheritance: a uniform roll from a little below the weaker parent up to a quarter of the
     * headroom above the better one, scaled by room. Very different parents always lower that ceiling.
     */
    static int inheritStat(int mother, int father, double room, Random random) {
        double max = Math.max(1, HusbandryConfig.maxGenetics());
        double low = Math.min(mother, father) / max;
        double high = Math.max(mother, father) / max;
        double offset = Math.pow(high - low, 3) * 3.5;
        double top = high + ((1 - high) * room - offset) / 4.0;
        double bottom = low - (1 - low) / 5.5;
        double result = Math.min(top, bottom) + random.nextDouble() * Math.abs(top - bottom);
        return clamp((int) Math.round(result * max), (int) max);
    }

    private static int roll(int max, Random random) {
        return max <= 0 ? 0 : random.nextInt(max + 1);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }
}
